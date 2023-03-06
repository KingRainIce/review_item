package com.ice.learning.review_pro.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Title: SimpleRedisLock
 * @Auth: Ice
 * @Date: 2023/3/5 19:18
 * @Version: 1.0
 * @Desc:
 */

public class SimpleRedisLock implements ILock {

    private String name;

    private StringRedisTemplate template;

    private static final String KEY_PREFIX = "lock:";

    // 注意：hutool包下的UUID方法
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate template) {
        this.name = name;
        this.template = template;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁标识
        String id = template.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            // 释放锁
            template.delete(KEY_PREFIX + name);
        }
    }
}
