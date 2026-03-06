package com.spark.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.security.dto.AuthResponse;
import com.spark.security.dto.LoginRequest;
import com.spark.security.dto.RegisterRequest;
import com.spark.security.entity.User;
import com.spark.security.mapper.UserMapper;
import com.spark.security.security.UserDetailsImpl;
import com.spark.security.service.UserService;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 * 负责处理用户注册、登录等核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final RedisUtils redisUtils;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("开始处理用户注册请求，用户名: {}", request.getUsername());
        
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            log.warn("用户注册失败：用户名 [{}] 已存在", request.getUsername());
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // 密码加密
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        log.debug("准备保存新用户信息: {}", user.getUsername());
        userMapper.insert(user);
        log.info("新用户信息保存成功，用户名: {}", user.getUsername());
        
        return generateTokensAndSave(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("开始处理用户登录请求，用户名: {}", request.getUsername());
        
        // 利用 Spring Security 的 AuthenticationManager 进行认证
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            log.debug("用户 [{}] 认证成功", request.getUsername());
        } catch (Exception e) {
            log.warn("用户 [{}] 认证失败: {}", request.getUsername(), e.getMessage());
            throw e;
        }
        
        // 认证成功后查询用户信息并生成 Token
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
                
        if (user == null) {
            log.error("认证成功但无法找到用户记录: {}", request.getUsername());
            throw new RuntimeException("用户不存在");
        }
        
        return generateTokensAndSave(user);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.info("开始处理刷新 Token 请求");

        // 1. 从 Token 中提取用户名
        String username = null;
        try {
            username = jwtUtils.extractUsername(refreshToken);
        } catch (Exception e) {
            log.warn("提取 refreshToken 的 username 失败: {}", e.getMessage());
            throw new RuntimeException("无效的 Refresh Token");
        }

        if (username == null) {
            throw new RuntimeException("无效的 Refresh Token");
        }

        // 2. 检查 Redis 中该用户的 Refresh Token 是否存在并且匹配
        String redisKey = "refresh_token:" + username;
        Object cachedToken = redisUtils.get(redisKey);
        
        if (cachedToken == null || !cachedToken.equals(refreshToken)) {
            log.warn("Redis 中未找到匹配的 Refresh Token 或已过期，用户名: {}", username);
            throw new RuntimeException("Refresh Token 已过期或被撤销，请重新登录");
        }

        // 3. 从数据库中获取最新的用户信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 4. 重新生成并返回新的 Tokens
        return generateTokensAndSave(user);
    }

    /**
     * 生成 AccessToken 和 RefreshToken，并将 RefreshToken 保存到 Redis
     */
    private AuthResponse generateTokensAndSave(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        
        log.debug("正在为用户 [{}] 生成 JWT Tokens...", user.getUsername());
        String accessToken = jwtUtils.generateToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);
        
        // 保存 RefreshToken 到 Redis
        String redisKey = "refresh_token:" + user.getUsername();
        // 将毫秒转换为秒
        redisUtils.set(redisKey, refreshToken, refreshExpiration / 1000);
        
        log.info("用户 [{}] Token 生成并保存成功", user.getUsername());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}