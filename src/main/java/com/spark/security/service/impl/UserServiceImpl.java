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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        
        // 生成 Token
        log.debug("正在为用户 [{}] 生成 JWT Token...", user.getUsername());
        String jwtToken = jwtUtils.generateToken(new UserDetailsImpl(user));
        
        log.info("用户 [{}] 注册流程完成", user.getUsername());
        return AuthResponse.builder().token(jwtToken).build();
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
        
        log.debug("正在为用户 [{}] 生成登录 JWT Token...", user.getUsername());
        String jwtToken = jwtUtils.generateToken(new UserDetailsImpl(user));
        
        log.info("用户 [{}] 登录成功", user.getUsername());
        return AuthResponse.builder().token(jwtToken).build();
    }
}