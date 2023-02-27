package com.ice.learning.review_pro.controller;


import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.ShopType;
import com.ice.learning.review_pro.service.IShopTypeService;
import com.ice.learning.review_pro.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        //1: 从redis中查询是否有店铺列表信息
        String key = SystemConstants.SHOP_TYPE_LIST;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotEmpty(cacheShop)) {
            //2: 有直接返回;
            List<ShopType> shopTypes = Convert.toList(ShopType.class, cacheShop);
            return Result.ok(shopTypes);
        }
        //3：没有、去数据库中查询
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            //4：没有
            return Result.fail("没有商品类别");
        }
        //5：有,将店铺数据写入redis中
        stringRedisTemplate.opsForValue().set(key, Convert.toStr(typeList));
        //6：返回店铺列表数据
        return Result.ok(typeList);
    }
}
