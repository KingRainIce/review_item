package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.mapper.ShopMapper;
import com.ice.learning.review_pro.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @Auth: Ice
     * @Date: 2023/2/27 21:00
     * @Desc: 互斥锁解决缓存击穿
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        // 1.从Redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get("shop:key:" + id);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在则直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }
        // 4.实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = "shop:lock:" + id;
        Shop shop = null;
        try {
            // 4.2 判断是否为空
            if (!tryLock(lockKey)) {
                // 4.3 为空则休眠100ms后重试
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            // 4.4 成功，从数据库中查询
            shop = getById(id);
            // 5.不存在，将空值写入redis，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set("shop:key:" + id, "", 5, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在，将数据写入redis，返回数据
            stringRedisTemplate.opsForValue().set("shop:key:" + id, JSONUtil.toJsonStr(shop), 5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 4.7 释放锁
            unLock(lockKey);
        }
        return shop;
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

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
