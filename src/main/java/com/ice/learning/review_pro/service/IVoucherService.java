package com.ice.learning.review_pro.service;

import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
