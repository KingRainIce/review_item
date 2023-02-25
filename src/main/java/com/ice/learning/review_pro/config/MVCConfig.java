package com.ice.learning.review_pro.config;

import com.ice.learning.review_pro.interceptors.LoginInterceptors;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Title: MVCConfig
 * @Auth: Ice
 * @Date: 2023/2/25 13:50
 * @Version: 1.0
 * @Desc:
 */

@Configuration
public class MVCConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptors())
                .excludePathPatterns("/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/upload/**");
    }
}
