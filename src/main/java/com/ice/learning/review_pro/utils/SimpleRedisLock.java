package com.ice.learning.review_pro.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Title: SimpleRedisLock
 * @Auth: Ice
 * @Date: 2023/3/5 19:18
 * @Version: 1.0
 * @Desc:
 */

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate template;
    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate template) {
        this.name = name;
        this.template = template;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        Long threadId = Thread.currentThread().getId();
        // 获取锁
        Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        template.delete(KEY_PREFIX + name);
    }
}
