package com.ice.learning.review_pro.service;

import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);
}
