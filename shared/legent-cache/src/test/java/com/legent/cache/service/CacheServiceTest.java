package com.legent.cache.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new CacheService(redisTemplate);
    }

    @Test
    void setWithTtl_writesToRedisWithExpiry() {
        cacheService.set("k1", "v1", Duration.ofMinutes(5));

        verify(valueOperations).set("k1", "v1", Duration.ofMinutes(5));
    }

    @Test
    void get_whenValueExists_returnsTypedValue() {
        when(valueOperations.get("k1")).thenReturn("v1");

        Optional<String> value = cacheService.get("k1", String.class);

        assertTrue(value.isPresent());
        assertEquals("v1", value.get());
    }

    @Test
    void get_whenValueMissing_returnsEmpty() {
        when(valueOperations.get("k2")).thenReturn(null);

        Optional<String> value = cacheService.get("k2", String.class);

        assertTrue(value.isEmpty());
    }
}
