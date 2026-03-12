package com.spark.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spark.security.dto.AuthResponse;
import com.spark.security.dto.LoginRequest;
import com.spark.security.dto.RegisterRequest;
import com.spark.security.entity.User;
import com.spark.security.mapper.UserMapper;
import com.spark.security.security.UserDetailsImpl;
import com.spark.security.service.BlacklistService;
import com.spark.security.service.UserService;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    private final BlacklistService blacklistService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${jwt.expiration}")
    private long accessTokenExpiration;

    private static final String CLIENT_TYPE_PC = "PC";

    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("开始处理用户注册请求，用户名: {}", request.getUsername());

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            log.warn("用户注册失败：用户名 [{}] 已存在", request.getUsername());
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        user.setPv(1L);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("新用户信息保存成功，用户名: {}", user.getUsername());

        return generateTokensAndSave(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("开始处理用户登录请求，用户名: {}", request.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("用户 [{}] 认证成功", request.getUsername());
        } catch (Exception e) {
            log.warn("用户 [{}] 认证失败: {}", request.getUsername(), e.getMessage());
            throw e;
        }

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

        String jti;
        try {
            jti = jwtUtils.extractJti(refreshToken);
        } catch (Exception e) {
            log.warn("提取 refreshToken 的 jti 失败: {}", e.getMessage());
            throw new RuntimeException("无效的 Refresh Token");
        }

        String refreshDataKey = "refresh_token:" + jti;
        Object cachedUsername = redisUtils.get(refreshDataKey);

        if (cachedUsername == null) {
            log.warn("Redis 中未找到匹配的 Refresh Token JTI 或已过期: {}", jti);
            throw new RuntimeException("Refresh Token 已过期或被撤销，请重新登录");
        }

        // 删除已使用的 Refresh Token (确保一次性)
        redisUtils.del(refreshDataKey);

        String username = (String) cachedUsername;
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return generateTokensAndSave(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        log.info("开始处理用户登出请求");

        if (accessToken != null && !accessToken.isEmpty()) {
            blacklistService.banToken(accessToken);
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            return;
        }

        try {
            String jti = jwtUtils.extractJti(refreshToken);
            if (jti != null) {
                // 删除 Refresh Token 的核心数据
                redisUtils.del("refresh_token:" + jti);

                // 清理用户设备上的 Refresh Token 指针
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null) {
                    Object principal = authentication.getPrincipal();
                    if (principal instanceof UserDetailsImpl) {
                         Long userId = ((UserDetailsImpl) principal).getId();
                         String refreshUserKey = "refresh_user:" + userId + ":" + CLIENT_TYPE_PC;
                         redisUtils.del(refreshUserKey);
                    } else if (principal instanceof User) {
                        Long userId = ((User) principal).getId();
                        String refreshUserKey = "refresh_user:" + userId + ":" + CLIENT_TYPE_PC;
                        redisUtils.del(refreshUserKey);
                    }
                }
                log.info("用户登出成功，已清除 Refresh Token JTI: {}", jti);
            }
        } catch (Exception e) {
            log.warn("登出时处理 refreshToken 失败: {}", e.getMessage());
        }
    }

    @Override
    public void changePassword(com.spark.security.dto.ChangePasswordRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = null;
        if (principal instanceof UserDetailsImpl) {
            userId = ((UserDetailsImpl) principal).getId();
        } else if (principal instanceof User) {
            userId = ((User) principal).getId();
        }
        
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPv((user.getPv() == null ? 1L : user.getPv()) + 1L);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户 [{}] 修改密码成功，版本号已更新为 {}", user.getUsername(), user.getPv());
    }

    private AuthResponse generateTokensAndSave(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        Long pv = user.getPv() != null ? user.getPv() : 1L;

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("pv", pv);

        String accessToken = jwtUtils.generateToken(extraClaims, userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);
        String newAccessTokenJti = jwtUtils.extractJti(accessToken);
        String newRefreshTokenJti = jwtUtils.extractJti(refreshToken);

        // --- Access Token 单点登录逻辑 ---
        String userAccessKey = "access_token:" + user.getId() + ":" + CLIENT_TYPE_PC;
        Object oldAccessJtiObj = redisUtils.get(userAccessKey);
        if (oldAccessJtiObj != null) {
            String oldAccessJti = (String) oldAccessJtiObj;
            if (!oldAccessJti.equals(newAccessTokenJti)) {
                log.info("检测到用户 [{}] 在 [{}] 端存在旧的 Access Token (JTI: {})，正在踢出...", user.getUsername(), CLIENT_TYPE_PC, oldAccessJti);
                blacklistService.banJti(oldAccessJti, accessTokenExpiration);
            }
        }
        redisUtils.set(userAccessKey, newAccessTokenJti, accessTokenExpiration / 1000);

        // --- Refresh Token 单点登录逻辑 ---
        String refreshUserKey = "refresh_user:" + user.getId() + ":" + CLIENT_TYPE_PC;
        Object oldRefreshJtiObj = redisUtils.get(refreshUserKey);
        if (oldRefreshJtiObj != null) {
            String oldRefreshJti = (String) oldRefreshJtiObj;
            log.info("检测到用户 [{}] 在 [{}] 端存在旧的 Refresh Token (JTI: {})，正在使其失效...", user.getUsername(), CLIENT_TYPE_PC, oldRefreshJti);
            redisUtils.del("refresh_token:" + oldRefreshJti);
        }

        // 1. 存储新的 Refresh Token 数据 (JTI -> username)
        redisUtils.set("refresh_token:" + newRefreshTokenJti, user.getUsername(), refreshExpiration / 1000);
        // 2. 更新用户设备指向的最新 JTI
        redisUtils.set(refreshUserKey, newRefreshTokenJti, refreshExpiration / 1000);

        log.info("用户 [{}] Token 生成并保存成功, Access JTI: {}, Refresh JTI: {}", user.getUsername(), newAccessTokenJti, newRefreshTokenJti);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}