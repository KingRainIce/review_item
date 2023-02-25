package com.ice.learning.review_pro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ice.learning.review_pro.DTO.LoginFormDTO;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
