package com.spark.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author w'z'x
 * @version 1.0
 * @description: 布隆过滤器配置属性
 * @date 2026/3/9 20:17
 */
@Data
@Component
@ConfigurationProperties(prefix = "bloom-filter")
public class BloomFilterProperties {
    /**
     * 预期插入数量
     */
    private long expectedInsertions = 1000;

    /**
     * 误判率
     */
    private double falseProbability = 0.01;
}
