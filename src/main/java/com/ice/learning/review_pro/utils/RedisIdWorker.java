package com.ice.learning.review_pro.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Title: RedisWorker
 * @Auth: Ice
 * @Date: 2023/2/28 12:45
 * @Version: 1.0
 * @Desc:
 */

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate template;

    // 2021-01-01 00:00:00
    private static final long BEGIN_STAMP = 1640995200L;

    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_STAMP;
        // 2.生成序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = template.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接并返回,+可以取代|
        return timestamp << COUNT_BITS | count;
    }
}
