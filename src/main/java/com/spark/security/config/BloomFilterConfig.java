package com.spark.security.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author w'z'x
 * @version 1.0
 * @description: 布隆过滤器配置类
 * @date 2026/3/9 20:17
 */
@Configuration
@RequiredArgsConstructor
public class BloomFilterConfig {

    private final BloomFilterProperties bloomFilterProperties;

    @Bean
    public RBloomFilter<String> tokenBlackListBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("spark-security:auth:blacklist:bloom");
        bloomFilter.tryInit(bloomFilterProperties.getExpectedInsertions(), bloomFilterProperties.getFalseProbability());
        return bloomFilter;
    }
}
