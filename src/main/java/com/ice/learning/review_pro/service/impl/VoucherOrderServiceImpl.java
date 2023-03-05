package com.ice.learning.review_pro.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.SeckillVoucher;
import com.ice.learning.review_pro.entity.VoucherOrder;
import com.ice.learning.review_pro.mapper.VoucherOrderMapper;
import com.ice.learning.review_pro.service.ISeckillVoucherService;
import com.ice.learning.review_pro.service.IVoucherOrderService;
import com.ice.learning.review_pro.utils.RedisIdWorker;
import com.ice.learning.review_pro.utils.SimpleRedisLock;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate template;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }

        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        // 创建锁对象
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, template);
        //获取锁
        boolean isLock = simpleRedisLock.tryLock(1200);
        if (!isLock){
            return Result.fail("Gun");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            simpleRedisLock.unLock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 4.一人一单，查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次！");
        }
        // 5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }
        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7.返回订单ID
        return Result.ok(orderId);
    }
}
