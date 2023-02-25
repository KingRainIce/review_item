package com.ice.learning.review_pro.interceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.utils.SystemConstants;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Title: RefreshTokenInterceptor
 * @Auth: Ice
 * @Date: 2023/2/25 23:11
 * @Version: 1.0
 * @Desc:
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头的 token,key和前端的代码有关
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 2.基于token获取redis的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(SystemConstants.LOGIN_USER_PREFIX + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            return true;
        }
        // 5.将查询到的Hash数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6.存在保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        // 7.刷新redis的过期时间
        stringRedisTemplate.expire(SystemConstants.LOGIN_USER_PREFIX + token, 30, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
