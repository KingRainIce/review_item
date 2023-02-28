package com.ice.learning.review_pro.interceptors;

import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @title: LoginInterceptors
 * @Author Ice
 * @Date: 2023/2/25 12:59
 * @Version 1.0
 */

public class LoginInterceptors implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        // 有用户放行
        return true;
    }

}
