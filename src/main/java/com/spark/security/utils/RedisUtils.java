package com.spark.security.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis 工具类
 * 提供对 Redis 的各种快捷操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.redis.key-pre:spark}")
    private String keyPre;

    private static final String COLON = ":";

    /**
     * 内部私有方法，统一管理 Key 前缀逻辑
     * 所有的操作都会经过此方法构造 Key
     */
    private String buildKey(String key) {
        if (key == null) return null;
        // 如果 key 已经包含了前缀，则不再重复拼接
        if (key.startsWith(keyPre + COLON)) {
            return key;
        }
        return keyPre + COLON + key;
    }

    // ============================= 基础操作 =============================

    /**
     * 指定缓存失效时间
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(buildKey(key), time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis expire 异常，key: {}, time: {}", key, time, e);
            return false;
        }
    }

    /**
     * 根据 key 获取过期时间
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(buildKey(key), TimeUnit.SECONDS);
    }

    /**
     * 判断 key 是否存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(key)));
        } catch (Exception e) {
            log.error("Redis hasKey 异常，key: {}", key, e);
            return false;
        }
    }

    /**
     * 删除缓存
     */
    public void del(String... key) {
        if (key != null && key.length > 0) {
            try {
                if (key.length == 1) {
                    redisTemplate.delete(buildKey(key[0]));
                } else {
                    Collection<String> keys = Arrays.stream(key)
                            .map(this::buildKey)
                            .collect(Collectors.toList());
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.error("Redis del 异常，keys: {}", Arrays.toString(key), e);
            }
        }
    }

    /**
     * 获取符合条件的 key
     */
    public Collection<String> keys(String pattern) {
        try {
            return redisTemplate.keys(buildKey(pattern));
        } catch (Exception e) {
            log.error("Redis keys 异常，pattern: {}", pattern, e);
            return null;
        }
    }

    // ============================ String（字符串操作）=============================

    /**
     * 普通缓存获取
     */
    public Object get(String key) {
        try {
            return key == null ? null : redisTemplate.opsForValue().get(buildKey(key));
        } catch (Exception e) {
            log.error("Redis get 异常，key: {}", key, e);
            return null;
        }
    }

    /**
     * 普通缓存放入
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(buildKey(key), value);
            return true;
        } catch (Exception e) {
            log.error("Redis set 异常，key: {}", key, e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(buildKey(key), value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis set with time 异常，key: {}, time: {}", key, time, e);
            return false;
        }
    }

    // ================================ Map（哈希表操作）=================================

    /**
     * HashGet
     */
    public Object hget(String key, String item) {
        try {
            return redisTemplate.opsForHash().get(buildKey(key), item);
        } catch (Exception e) {
            log.error("Redis hget 异常，key: {}, item: {}", key, item, e);
            return null;
        }
    }

    /**
     * 获取 hashKey 对应的所有键值
     */
    public Map<Object, Object> hmget(String key) {
        try {
            return redisTemplate.opsForHash().entries(buildKey(key));
        } catch (Exception e) {
            log.error("Redis hmget 异常，key: {}", key, e);
            return null;
        }
    }

    /**
     * HashSet
     */
    public boolean hmset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(buildKey(key), map);
            return true;
        } catch (Exception e) {
            log.error("Redis hmset 异常，key: {}", key, e);
            return false;
        }
    }

    /**
     * 向一张 hash 表中放入数据
     */
    public boolean hset(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(buildKey(key), item, value);
            return true;
        } catch (Exception e) {
            log.error("Redis hset 异常，key: {}, item: {}", key, item, e);
            return false;
        }
    }

    /**
     * 删除 hash 表中的值
     */
    public void hdel(String key, Object... item) {
        try {
            redisTemplate.opsForHash().delete(buildKey(key), item);
        } catch (Exception e) {
            log.error("Redis hdel 异常，key: {}, item: {}", key, Arrays.toString(item), e);
        }
    }
}