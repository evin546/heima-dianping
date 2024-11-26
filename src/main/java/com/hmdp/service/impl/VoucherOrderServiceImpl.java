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
import com.hmdp.utils.VoucherOrderConstants;
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

    @Override
    @Transactional
    public Result placeOrder(Long voucherId) {
        LocalDateTime now = LocalDateTime.now();
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(now.isBefore(voucher.getBeginTime()) || now.isAfter(voucher.getEndTime())){
            return Result.fail("当前不在秒杀时段内！");
        }

        Integer stock = voucher.getStock();
        if(stock < 1){
            return Result.fail("当前已售罄！");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).update();
        if(!success){
            return Result.fail("下单失败");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(VoucherOrderConstants.STATUS_UNPAID);

        save(voucherOrder);

        return Result.ok(voucherOrder.getId());
    }
}
