package com.spark.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spark.security.entity.User;
import com.spark.security.mapper.UserMapper;
import com.spark.security.service.BlacklistService;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistServiceImpl implements BlacklistService {

    private final RedisUtils redisUtils;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    public void banToken(String token) {
        log.info("开始处理封禁 Token 请求");
        try {
            Date expirationDate = jwtUtils.extractExpiration(token);
            long expireTimeSec = (expirationDate.getTime() - System.currentTimeMillis()) / 1000;

            if (expireTimeSec > 0) {
                // 以 token 本身或其 hash 作为 key 存入 Redis
                String redisKey = "blacklist:token:" + token;
                redisUtils.set(redisKey, "banned", expireTimeSec);
                log.info("Token 已加入黑名单，将在 {} 秒后从 Redis 清除", expireTimeSec);
            }
        } catch (Exception e) {
            log.warn("加入黑名单失败，Token 可能已过期或无效: {}", e.getMessage());
        }
    }

    @Override
    public void banUser(String username) {
        log.info("开始处理封禁用户请求: {}", username);

        // 1. 修改数据库中用户的状态为 0 (禁用)
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUsername, username)
                     .set(User::getStatus, 0);
        int updated = userMapper.update(null, updateWrapper);

        if (updated > 0) {
            log.info("用户 [{}] 数据库状态已更新为禁用", username);
        } else {
            log.warn("封禁失败：未找到用户 [{}]", username);
            return;
        }

        // 2. 删除 Redis 中的 Refresh Token，让用户无法再换取新的 Access Token
        String redisKey = "refresh_token:" + username;
        redisUtils.del(redisKey);
        log.info("已删除用户 [{}] 的 Refresh Token，阻止后续刷新", username);
    }
}