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
    private final com.spark.security.service.BlacklistService blacklistService;

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
        user.setPv(1L);
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
            // 从 Token 中提取用户名
            String username = jwtUtils.extractUsername(refreshToken);
            if (username != null) {
                // 删除 Redis 中的 Token
                String redisKey = "refresh_token:" + username;
                redisUtils.del(redisKey);
                log.info("用户 [{}] 登出成功，已清除 Redis 中的 Refresh Token", username);
            }
        } catch (Exception e) {
            log.warn("登出时提取 refreshToken 的 username 失败: {}", e.getMessage());
            // 忽略异常，确保登出流程顺利完成
        }
    }

    @Override
    public void changePassword(com.spark.security.dto.ChangePasswordRequest request) {
        Long userId = com.spark.security.utils.UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 校验旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 更新密码和版本号
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPv((user.getPv() == null ? 1L : user.getPv()) + 1L);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 删除 Redis 中的 Refresh Token，使所有设备强制下线（需要重新登录获取新Token）
        String redisKey = "refresh_token:" + user.getUsername();
        redisUtils.del(redisKey);
        log.info("用户 [{}] 修改密码成功，版本号已更新为 {}，已清除所有 Refresh Token", user.getUsername(), user.getPv());
    }

    /**
     * 生成 AccessToken 和 RefreshToken，并将 RefreshToken 保存到 Redis
     */
    private AuthResponse generateTokensAndSave(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        
        Long pv = user.getPv() != null ? user.getPv() : 1L;

        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("pv", pv);

        log.debug("正在为用户 [{}] 生成 JWT Tokens...", user.getUsername());
        // 生成包含 pv 的 AccessToken
        String accessToken = jwtUtils.generateToken(extraClaims, userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);
        
        // 保存 RefreshToken 到 Redis
        String redisKey = "refresh_token:" + user.getUsername();
        // 将毫秒转换为秒
        redisUtils.set(redisKey, refreshToken, refreshExpiration / 1000);
        
        log.info("用户 [{}] Token 生成并保存成功，当前版本号: {}", user.getUsername(), pv);
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}