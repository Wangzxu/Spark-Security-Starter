package com.spark.security.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spark.security.dto.AuthResponse;
import com.spark.security.dto.LoginRequest;
import com.spark.security.dto.RegisterRequest;
import com.spark.security.entity.User;

/**
 * 用户服务接口
 * 定义了用户相关的核心业务操作，如注册、登录等
 */
public interface UserService extends IService<User> {
    
    /**
     * 用户注册
     * 接收用户的注册信息，校验通过后保存到数据库并返回携带 Token 的认证响应
     * 
     * @param request 注册请求信息，包含用户名、密码、昵称等
     * @return 注册成功的响应，包含用于后续身份验证的 JWT token
     * @throws RuntimeException 如果用户名已存在则抛出异常
     */
    AuthResponse register(RegisterRequest request);

    /**
     * 用户登录
     * 验证用户的登录凭证，验证成功后生成并返回 JWT Token
     * 
     * @param request 登录请求信息，包含用户名和密码
     * @return 登录成功的响应，包含用于后续身份验证的 JWT token
     * @throws org.springframework.security.core.AuthenticationException 如果认证失败则抛出异常
     */
    AuthResponse login(LoginRequest request);

    /**
     * 刷新 Token
     * 根据传入的 refreshToken 重新生成新的 accessToken 和 refreshToken
     * 
     * @param refreshToken 请求刷新的 Token
     * @return 包含新 Token 的认证响应
     */
    AuthResponse refreshToken(String refreshToken);

    /**
     * 退出登录
     * 
     * @param accessToken 当前的 Access Token
     * @param refreshToken 请求退出的 Token
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 修改密码
     * 
     * @param request 修改密码请求
     */
    void changePassword(com.spark.security.dto.ChangePasswordRequest request);
}