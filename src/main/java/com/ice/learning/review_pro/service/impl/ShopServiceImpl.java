package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.mapper.ShopMapper;
import com.ice.learning.review_pro.service.IShopService;
import com.ice.learning.review_pro.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * @Auth: Ice
     * @Date: 2023/2/27 21:00
     * @Desc: 逻辑删除解决缓存击穿
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithLogicalExpire(id);
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

    public Shop queryWithLogicalExpire(Long id) {
        // 1.从Redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get("shop:key:" + id);
        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.不存在直接返回
            return null;
        }
        // 4.命中，将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        // 5.1 未过期，直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        // 5.2 已过期，重建缓存
        // 6.缓存重建
        // 6.1 获取互斥锁
        String lockKey = "shop:lock:" + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断获取锁是否成功
        if (isLock) {
            // 6.3 成功，开启线程池，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.4 返回过期的商铺信息
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

    public void saveShop2Redis(Long id, Long expireTime) {
        // 1.从数据库中查询
        Shop shop = getById(id);
        //2.封装过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        // 3.将数据写入redis
        stringRedisTemplate.opsForValue().set("shop:key:" + id, JSONUtil.toJsonStr(shop), expireTime, TimeUnit.SECONDS);
    }

}
