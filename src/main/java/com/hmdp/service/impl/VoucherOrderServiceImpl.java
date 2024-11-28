package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.constant.VoucherOrderConstants;
import com.hmdp.utils.lock.SimpleRedisLock;
import com.hmdp.utils.lock.SimpleRedisLockV2;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result placeVoucherOrder(Long voucherId) {
        if(voucherId == null){
            return Result.fail("下单参数异常！");
        }
        LocalDateTime now = LocalDateTime.now();
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("下单参数异常！");
        }
        if(now.isBefore(voucher.getBeginTime()) || now.isAfter(voucher.getEndTime())){
            return Result.fail("当前不在秒杀时段内！");
        }
        Long userId = UserHolder.getUser().getId();

        /* 已替换成分布式锁
        synchronized (userId.toString().intern()){
            IVoucherOrderService voucherService = (VoucherOrderServiceImpl)AopContext.currentProxy();
            return voucherService.createVoucherOrder(voucherId, userId, voucher);
        }*/

        //创建锁对象
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId.toString());
        SimpleRedisLockV2 simpleRedisLock = new SimpleRedisLockV2(stringRedisTemplate,"order:" + userId.toString());
        //尝试获取锁:
        if(!simpleRedisLock.tryLock(5)){
            //获取锁失败
            return Result.fail("不允许重复下单！");
        }

        try {
            IVoucherOrderService voucherService = (VoucherOrderServiceImpl)AopContext.currentProxy();
            return voucherService.createVoucherOrder(voucherId, userId, voucher);
        } finally {
            //释放锁
            simpleRedisLock.unlock();
        }


    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId, SeckillVoucher voucher) {
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if(count > 0){
            return Result.fail("该商品一名用户仅能下单一次！");
        }

        Integer stock = voucher.getStock();
        if(stock < 1){
            return Result.fail("当前已售罄！");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if(!success){
            return Result.fail("当前已售罄！");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(VoucherOrderConstants.STATUS_UNPAID);

        save(voucherOrder);

        return Result.ok(voucherOrder.getId());
    }
}
