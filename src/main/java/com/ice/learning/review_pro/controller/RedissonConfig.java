package com.ice.learning.review_pro.controller;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Title: RedissonConfig
 * @Auth: Ice
 * @Date: 2023/3/14 9:14
 * @Version: 1.0
 * @Desc:
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("2003");
        return Redisson.create(config);

    }
}
