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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result addVoucherOrder(Long voucherId) {
        //1、根据秒杀优惠券id去tb_seckill_voucher表查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2、根据查到得优惠券判定当前活动是否开始
        //虽然前端做了，但前端很好绕过，因此后端也要校验
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动尚未开始！");
        }
        //3、查询库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足1！");
        }
        //4、减扣库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if(!isSuccess){
            return Result.fail("库存不足2！");
        }
        //5、创建订单，包括订单id，用户id以及代金券id
        long orderId = redisIdWorker.nextId("order_");
        VoucherOrder voucherOrder = VoucherOrder
                .builder()
                .id(orderId)
                .userId(UserHolder.getUser().getId())
                .voucherId(voucherId)
                .build();
        //6、写进voucher_order表
        save(voucherOrder);
        //7、返回订单id
        return Result.ok(orderId);
    }
}
