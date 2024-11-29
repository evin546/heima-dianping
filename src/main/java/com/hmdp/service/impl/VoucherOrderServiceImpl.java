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
import com.hmdp.utils.constant.RedisConstants;
import com.hmdp.utils.constant.VoucherOrderConstants;
import com.hmdp.utils.lock.SimpleRedisLock;
import com.hmdp.utils.lock.SimpleRedisLockV2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

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

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_QUALIFICATION_CHECK;

    static {
        SECKILL_QUALIFICATION_CHECK = new DefaultRedisScript<>();
        SECKILL_QUALIFICATION_CHECK.setLocation(new ClassPathResource("seckill_qualification_check.lua"));
        SECKILL_QUALIFICATION_CHECK.setResultType(Long.class);
    }

    public Result placeVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_QUALIFICATION_CHECK,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        if(result == null){
            return Result.fail("下单失败！");
        }
        long r = result.longValue();
        if(r != 0L){
            return Result.fail(r == 1L ? "当前已售罄！" : "不允许重复下单！");
        }

        return Result.ok(orderId);
    }

/*  改为使用Redis判断下单资格
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

        *//* 已替换成分布式锁
        synchronized (userId.toString().intern()){
            IVoucherOrderService voucherService = (VoucherOrderServiceImpl)AopContext.currentProxy();
            return voucherService.createVoucherOrder(voucherId, userId, voucher);
        }*//*

        //创建锁对象
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId.toString());
        //SimpleRedisLockV2 simpleRedisLock = new SimpleRedisLockV2(stringRedisTemplate,"order:" + userId.toString());

        //使用Redisson：
        //尝试获取锁:
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();

        if(!isLock){
            //获取锁失败
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象，保证Spring事务生效
            IVoucherOrderService voucherService = (VoucherOrderServiceImpl)AopContext.currentProxy();
            return voucherService.createVoucherOrder(voucherId, userId, voucher);
        } finally {
            //释放锁
            lock.unlock();
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
    }*/
}
