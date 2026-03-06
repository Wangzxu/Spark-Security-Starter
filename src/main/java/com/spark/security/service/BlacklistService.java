package com.spark.security.service;

public interface BlacklistService {

    /**
     * 将 Token 加入黑名单
     * @param token 需要被封禁的 JWT
     */
    void banToken(String token);

    /**
     * 封禁用户，禁止其后续登录，并使其当前的 Refresh Token 失效
     * @param username 需要被封禁的用户名
     */
    void banUser(String username);
}