package com.spark.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spark.security.entity.User;
import com.spark.security.mapper.UserMapper;
import com.spark.security.service.BlacklistService;
import com.spark.security.utils.BloomFilterUtils;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistServiceImpl implements BlacklistService {

    private final RedisUtils redisUtils;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final BloomFilterUtils bloomFilterUtils;

    @Override
    public void banToken(String token) {
        log.info("开始处理封禁 Token 请求");
        try {
            String jti = jwtUtils.extractJti(token);
            if (jti == null) {
                log.warn("无法提取 Token 的 JTI，跳过封禁");
                return;
            }

            Date expirationDate = jwtUtils.extractExpiration(token);
            long expireTimeMs = expirationDate.getTime() - System.currentTimeMillis();

            if (expireTimeMs > 0) {
                banJti(jti, expireTimeMs);
            }
        } catch (Exception e) {
            log.warn("加入黑名单失败，Token 可能已过期或无效: {}", e.getMessage());
        }
    }

    @Override
    public void banJti(String jti, long expirationTimeMs) {
        if (expirationTimeMs <= 0) {
            return;
        }
        // 以 JTI 作为 key 存入 Redis
        String redisKey = "blacklist:token:" + jti;
        redisUtils.set(redisKey, "banned", expirationTimeMs / 1000);

        // 同时加入布隆过滤器
        bloomFilterUtils.add(jti);

        log.info("Token JTI [{}] 已加入黑名单和布隆过滤器，将在 {} 秒后从 Redis 清除", jti, expirationTimeMs / 1000);
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

        // 2. 删除 Redis 中的所有 Refresh Token，让用户无法再换取新的 Access Token
        String userTokensKey = "user_tokens:" + username;
        Set<Object> jtis = redisUtils.sGet(userTokensKey);
        if (jtis != null && !jtis.isEmpty()) {
            for (Object jtiObj : jtis) {
                String jti = (String) jtiObj;
                redisUtils.del("refresh_token:" + jti);
            }
            redisUtils.del(userTokensKey);
            log.info("已删除用户 [{}] 的所有 Refresh Token，阻止后续刷新", username);
        }
    }

    @Override
    public boolean isTokenBanned(String token) {
        String jti = jwtUtils.extractJti(token);
        if (jti == null) {
            return false;
        }

        // 1. 首先检查布隆过滤器
        if (!bloomFilterUtils.contains(jti)) {
            // 布隆过滤器说不在，那就肯定不在
            return false;
        }

        // 2. 布隆过滤器说在（可能误判），进一步去 Redis 确认
        log.debug("布隆过滤器命中，正在前往 Redis 确认 JTI: {}", jti);
        return redisUtils.hasKey("blacklist:token:" + jti);
    }
}