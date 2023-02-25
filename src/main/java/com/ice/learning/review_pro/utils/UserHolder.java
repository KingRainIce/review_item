package com.ice.learning.review_pro.utils;

import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.entity.User;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
