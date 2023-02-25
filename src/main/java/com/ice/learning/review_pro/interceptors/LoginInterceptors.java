package com.ice.learning.review_pro.interceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.utils.SystemConstants;
import com.ice.learning.review_pro.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

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
