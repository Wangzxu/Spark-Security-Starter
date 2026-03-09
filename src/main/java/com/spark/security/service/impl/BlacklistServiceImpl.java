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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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
            Date expirationDate = jwtUtils.extractExpiration(token);
            long expireTimeSec = (expirationDate.getTime() - System.currentTimeMillis()) / 1000;

            if (expireTimeSec > 0) {
                // 以 token 本身或其 hash 作为 key 存入 Redis
                String redisKey = "blacklist:token:" + token;
                redisUtils.set(redisKey, "banned", expireTimeSec);
                
                // 同时加入布隆过滤器
                bloomFilterUtils.add(token);
                
                log.info("Token 已加入黑名单和布隆过滤器，将在 {} 秒后从 Redis 清除", expireTimeSec);
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

    @Override
    public boolean isTokenBanned(String token) {
        // 1. 首先检查布隆过滤器
        if (!bloomFilterUtils.contains(token)) {
            // 布隆过滤器说不在，那就肯定不在
            return false;
        }

        // 2. 布隆过滤器说在（可能误判），进一步去 Redis 确认
        log.debug("布隆过滤器命中，正在前往 Redis 确认: {}", token);
        return redisUtils.hasKey("blacklist:token:" + token);
    }

    /**
     * 定时刷新布隆过滤器，降低误判率
     * 采用双缓冲机制 (Double Buffering) 避免刷新期间的空窗期
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void refreshBloomFilter() {
        log.info("开始定时刷新布隆过滤器...");

        // 从 Redis 重新加载黑名单 Token
        Collection<String> keys = redisUtils.keys("blacklist:token:*");
        List<String> tokens = new ArrayList<>();
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                // 提取 Token
                String[] parts = key.split("blacklist:token:");
                if (parts.length > 1) {
                    tokens.add(parts[1]);
                }
            }
        }
        
        // 调用工具类进行刷新
        bloomFilterUtils.refresh(tokens);
        
        log.info("布隆过滤器刷新完成，已重新加载 {} 个 Token", tokens.size());
    }
}
