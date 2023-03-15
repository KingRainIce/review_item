package com.ice.learning.review_pro.service;

import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
