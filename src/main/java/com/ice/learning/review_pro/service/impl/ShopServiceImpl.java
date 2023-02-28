package com.ice.learning.review_pro.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.mapper.ShopMapper;
import com.ice.learning.review_pro.service.IShopService;
import com.ice.learning.review_pro.utils.CacheClient;
import com.ice.learning.review_pro.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * @Auth: Ice
     * @Date: 2023/2/27 21:00
     * @Desc: 逻辑删除解决缓存击穿
     */
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.MINUTES);

        // 解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id==null) {
            return Result.fail("商铺id不能为空");
        }
        // 1. 先更新数据库
        updateById(shop);
        // 2. 删除redis中的缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }

}
