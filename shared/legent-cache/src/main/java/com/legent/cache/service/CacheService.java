package com.legent.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Generic cache service wrapping RedisTemplate.
 * Provides get/set/delete with TTL and tenant-aware key generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Stores a value with a TTL.
     */
    public void set(String key, Object value, Duration ttl) {
        log.debug("Cache SET: key={}, ttl={}s", key, ttl.getSeconds());
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * Stores a value without expiry.
     */
    public void set(String key, Object value) {
        log.debug("Cache SET (no TTL): key={}", key);
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Retrieves a value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.debug("Cache MISS: key={}", key);
            return Optional.empty();
        }
        log.debug("Cache HIT: key={}", key);
        return Optional.of((T) value);
    }

    /**
     * Retrieves a value, returning the raw Object.
     */
    public Optional<Object> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value);
    }

    /**
     * Deletes a key from cache.
     */
    public void delete(String key) {
        log.debug("Cache DELETE: key={}", key);
        redisTemplate.delete(key);
    }

    /**
     * Deletes all keys matching a pattern.
     */
    public void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            log.debug("Cache DELETE pattern: {} ({} keys)", pattern, keys.size());
            redisTemplate.delete(keys);
        }
    }

    /**
     * Checks if a key exists.
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
