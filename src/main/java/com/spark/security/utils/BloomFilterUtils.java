package com.spark.security.utils;

import com.spark.security.config.BloomFilterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @author w'z'x
 * @version 1.0
 * @description: 布隆过滤器工具类，封装了添加、查询及双缓冲刷新逻辑
 * @date 2026/3/9
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterUtils {

    private final RedissonClient redissonClient;
    private final BloomFilterProperties bloomFilterProperties;

    private static final String FILTER_NAME = "spark-security:auth:blacklist:bloom";
    private static final String TEMP_FILTER_NAME = "spark-security:auth:blacklist:bloom:temp";
    
    // 新增 Refresh Token 布隆过滤器名称
    private static final String REFRESH_FILTER_NAME = "spark-security:auth:refresh:bloom";
    private static final String TEMP_REFRESH_FILTER_NAME = "spark-security:auth:refresh:bloom:temp";


    /**
     * 添加元素到黑名单布隆过滤器
     * @param value 待添加的元素
     */
    public void add(String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(FILTER_NAME);
        // 尝试初始化，如果已存在则忽略
        bloomFilter.tryInit(bloomFilterProperties.getExpectedInsertions(), bloomFilterProperties.getFalseProbability());
        bloomFilter.add(value);
    }

    /**
     * 判断元素是否存在于黑名单布隆过滤器中
     * @param value 待判断的元素
     * @return true: 可能存在; false: 一定不存在
     */
    public boolean contains(String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(FILTER_NAME);
        // 如果过滤器不存在，说明没有任何数据，直接返回 false
        if (!bloomFilter.isExists()) {
            return false;
        }
        return bloomFilter.contains(value);
    }

    /**
     * 添加 Refresh Token 到布隆过滤器 (白名单模式)
     * @param jti Refresh Token 的 JTI
     */
    public void addRefreshToken(String jti) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(REFRESH_FILTER_NAME);
        // 尝试初始化，如果已存在则忽略
        bloomFilter.tryInit(bloomFilterProperties.getExpectedInsertions(), bloomFilterProperties.getFalseProbability());
        bloomFilter.add(jti);
    }

    /**
     * 判断 Refresh Token 是否存在于布隆过滤器中 (白名单模式)
     * @param jti Refresh Token 的 JTI
     * @return true: 可能存在; false: 一定不存在
     */
    public boolean containsRefreshToken(String jti) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(REFRESH_FILTER_NAME);
        // 如果过滤器不存在，说明没有任何数据，直接返回 false
        if (!bloomFilter.isExists()) {
            return false;
        }
        return bloomFilter.contains(jti);
    }

    /**
     * 使用双缓冲机制刷新黑名单布隆过滤器
     * @param values 需要重新加载的数据集合
     */
    public void refresh(Collection<String> values) {
        log.info("开始刷新黑名单布隆过滤器...");
        RBloomFilter<String> tempFilter = redissonClient.getBloomFilter(TEMP_FILTER_NAME);

        // 1. 清理临时过滤器
        tempFilter.delete();

        // 2. 初始化临时过滤器
        tempFilter.tryInit(bloomFilterProperties.getExpectedInsertions(), bloomFilterProperties.getFalseProbability());

        // 3. 加载数据
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                tempFilter.add(value);
            }
        }

        // 4. 原子性重命名，替换主过滤器
        tempFilter.rename(FILTER_NAME);
        log.info("黑名单布隆过滤器刷新完成，重载数据条数: {}", values == null ? 0 : values.size());
    }

    /**
     * 使用双缓冲机制刷新 Refresh Token 白名单布隆过滤器
     * @param refreshTokens 需要重新加载的 Refresh Token JTI 集合
     */
    public void refreshRefreshTokens(Collection<String> refreshTokens) {
        log.info("开始刷新 Refresh Token 白名单布隆过滤器...");
        RBloomFilter<String> tempFilter = redissonClient.getBloomFilter(TEMP_REFRESH_FILTER_NAME);

        // 1. 清理临时过滤器
        tempFilter.delete();

        // 2. 初始化临时过滤器
        tempFilter.tryInit(bloomFilterProperties.getExpectedInsertions(), bloomFilterProperties.getFalseProbability());

        // 3. 加载数据
        if (refreshTokens != null && !refreshTokens.isEmpty()) {
            for (String token : refreshTokens) {
                tempFilter.add(token);
            }
        }

        // 4. 原子性重命名，替换主过滤器
        tempFilter.rename(REFRESH_FILTER_NAME);
        log.info("Refresh Token 白名单布隆过滤器刷新完成，重载数据条数: {}", refreshTokens == null ? 0 : refreshTokens.size());
    }
}
