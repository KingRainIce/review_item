package com.ice.learning.review_pro.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Title: CacheClient
 * @Auth: Ice
 * @Date: 2023/2/28 10:52
 * @Version: 1.0
 * @Desc:
 */

@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate template;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        template.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        template.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id, Class<R> type,
                                          Function<ID, R> dbFallBack,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis中查询商铺缓存
        String json = template.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在则直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            return null;
        }
        // 4.不存在则根据ID查询数据库
        R r = dbFallBack.apply(id);
        // 5.不存在，将空值写入Redis，返回错误
        if (r == null) {
            template.opsForValue().set(key, "", time, unit);
            return null;
        }
        // 6.存在则写入Redis
        this.set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                            ID id, Class<R> type,
                                            Function<ID, R> dbFallBack,
                                            Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis中查询商铺缓存
        String json = template.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在直接返回
            return null;
        }
        // 4.命中，将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        // 5.1 未过期，直接返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5.2 已过期，重建缓存
        // 6.缓存重建
        // 6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断获取锁是否成功
        if (isLock) {
            // 6.3 成功，开启线程池，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = template.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        template.delete(key);
    }
}