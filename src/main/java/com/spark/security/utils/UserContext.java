package com.spark.security.utils;

import lombok.Data;

import java.util.Optional;

/**
 * @author w'z'x
 * @version 1.0
 * @description: ThreadLocal 存入用户信息
 * @date 2026/3/5 15:56
 */
public class UserContext {
    private static final ThreadLocal<CurrentUserInfo> USER_THREAD_LOCAL = new ThreadLocal<>();

    @Data
    public static class CurrentUserInfo {
        private Long userId;     // 必存
        private String username; // 常用展示字段
    }

    public static Long getUserId() {
        return Optional.ofNullable(USER_THREAD_LOCAL.get())
                .map(CurrentUserInfo::getUserId)
                .orElse(null);
    }

    public static String getUserName() {
        return Optional.ofNullable(USER_THREAD_LOCAL.get())
                .map(CurrentUserInfo::getUsername)
                .orElse(null);
    }

    public static void setUserInfo(Long userId, String username) {
        CurrentUserInfo userInfo = new CurrentUserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        USER_THREAD_LOCAL.set(userInfo);
    }

    public static void clear(){
        USER_THREAD_LOCAL.remove();
    }
}
