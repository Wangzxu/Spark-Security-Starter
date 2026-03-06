package com.spark.security.controller;

import com.spark.security.common.Result;
import com.spark.security.dto.AuthResponse;
import com.spark.security.dto.LoginRequest;
import com.spark.security.dto.RegisterRequest;
import com.spark.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 提供用户注册、登录的接口端点
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户注册接口
     * @param request 注册请求信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("收到用户注册请求，请求数据：{}", request);
        try {
            AuthResponse response = userService.register(request);
            log.info("用户注册请求处理成功");
            return Result.success(response);
        } catch (Exception e) {
            log.error("用户注册请求处理失败，原因: {}", e.getMessage(), e);
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 用户登录接口
     * @param request 登录请求信息
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("收到用户登录请求，用户名：{}", request.getUsername());
        try {
            AuthResponse response = userService.login(request);
            log.info("用户登录请求处理成功");
            return Result.success(response);
        } catch (Exception e) {
            log.error("用户登录请求处理失败，用户名: {}，原因: {}", request.getUsername(), e.getMessage());
            return Result.error(401, "用户名或密码错误");
        }
    }

    /**
     * 刷新 Token 接口
     * @param request 刷新 Token 请求信息
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@RequestBody com.spark.security.dto.RefreshTokenRequest request) {
        log.info("收到刷新 Token 请求");
        try {
            AuthResponse response = userService.refreshToken(request.getRefreshToken());
            log.info("刷新 Token 请求处理成功");
            return Result.success(response);
        } catch (Exception e) {
            log.error("刷新 Token 请求处理失败，原因: {}", e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 退出登录接口
     * @param request 退出请求信息
     * @param authHeader 请求头中的 Authorization
     * @return 退出结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody com.spark.security.dto.RefreshTokenRequest request, 
                               @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("收到用户退出登录请求");
        try {
            String accessToken = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                accessToken = authHeader.substring(7);
            }
            userService.logout(accessToken, request.getRefreshToken());
            return Result.success();
        } catch (Exception e) {
            log.error("退出登录请求处理失败，原因: {}", e.getMessage());
            return Result.error(500, "退出登录失败");
        }
    }
}