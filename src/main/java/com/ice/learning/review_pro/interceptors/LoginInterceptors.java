package com.ice.learning.review_pro.interceptors;

import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @title: LoginInterceptors
 * @Author Ice
 * @Date: 2023/2/25 12:59
 * @Version 1.0
 */

public class LoginInterceptors implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取session
        HttpSession session = request.getSession();
        // 2.获取session中的用户
        Object user = session.getAttribute("user");
        // 3.判断用户是否存在
        if (user == null) {
            // 4.不存在则拦截
            response.setStatus(401);
            return false;
        }
        // 5.存在则保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 6.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }

}
