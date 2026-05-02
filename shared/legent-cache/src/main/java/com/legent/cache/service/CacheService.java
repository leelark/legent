package com.legent.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.List;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

/**
 * Generic cache service wrapping RedisTemplate.
 * Provides get/set/delete with TTL and tenant-aware key generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor

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
     * Retrieves a value by key with type-safe verification.
     * AUDIT-023: Validates runtime type matches expected type.
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.debug("Cache MISS: key={}", key);
            return Optional.empty();
        }
        // AUDIT-023: Type-safe validation
        if (!type.isInstance(value)) {
            log.warn("Cache type mismatch for key={}: expected={}, actual={}", 
                    key, type.getName(), value.getClass().getName());
            return Optional.empty();
        }
        log.debug("Cache HIT: key={}", key);
        return Optional.of(type.cast(value));
    }
    
    /**
     * AUDIT-023: Type-safe wrapper for String values with namespace.
     */
    public Optional<String> getString(String namespace, String key) {
        return get(namespace + ":" + key, String.class);
    }
    
    /**
     * AUDIT-023: Type-safe wrapper for Long values with namespace.
     */
    public Optional<Long> getLong(String namespace, String key) {
        return get(namespace + ":" + key, Long.class);
    }
    
    /**
     * AUDIT-023: Type-safe wrapper for Integer values with namespace.
     */
    public Optional<Integer> getInteger(String namespace, String key) {
        return get(namespace + ":" + key, Integer.class);
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
        log.debug("Cache DELETE pattern: {}", pattern);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    connection.keyCommands().del(cursor.next());
                }
            }
            return null;
        });
    }

    /**
     * Executes a Redis Lua script.
     */
    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    /**
     * Checks if a key exists.
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
