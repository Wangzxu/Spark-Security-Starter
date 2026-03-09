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

    /**
     * 检查 Token 是否在黑名单中
     * @param token 需要检查的 JWT
     * @return 是否被封禁
     */
    boolean isTokenBanned(String token);
}