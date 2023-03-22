package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.LoginFormDTO;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.entity.User;
import com.ice.learning.review_pro.mapper.UserMapper;
import com.ice.learning.review_pro.service.IUserService;
import com.ice.learning.review_pro.utils.RegexUtils;
import com.ice.learning.review_pro.utils.SystemConstants;
import com.ice.learning.review_pro.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4.保存验证码到 redis
        stringRedisTemplate.opsForValue().set(SystemConstants.PHONE_CODE_PREFIX + phone, code, 2, TimeUnit.MINUTES);
        // 5.发送验证码
        log.debug("发送验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(SystemConstants.PHONE_CODE_PREFIX + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3.不一致报错
            return Result.fail("验证码错误");
        }
        // 4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建用户并保存
            user = createUserWithPhone(phone);
        }
        // 7.保存用户信息到 redis
        // 7.1 生成 token,作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 将用户信息转换为 HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3 存储
        String tokenKey = SystemConstants.LOGIN_USER_PREFIX + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4 设置过期时间
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // Gets the currently logged-on user
        Long userId = UserHolder.getUser().getId();
        // Get the date
        LocalDateTime now = LocalDateTime.now();
        // Splicing key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId + keySuffix;
        // Get today is the day of the month
        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        // Get the date
        LocalDateTime now = LocalDateTime.now();
        // Splicing key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId + keySuffix;
        // Get today is the day of the month
        int day = now.getDayOfMonth();
        // Get all check-ins up to date this month
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // Loop traversal
        int count = 0;
        while (true){
            if ((num & 1) == 0){
                break;
            }else {
                count++;
            }
            num = num >>> 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


}
