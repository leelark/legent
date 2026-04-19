package com.legent.foundation.service;

import java.util.List;
import java.util.Optional;

import com.legent.cache.service.CacheService;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.mapper.ConfigMapper;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigService Unit Tests")
// @SuppressWarnings("null")
class ConfigServiceTest {

        @Mock
        private ConfigRepository configRepository;
        @Mock
        private ConfigMapper configMapper;
        @Mock
        private CacheService cacheService;
        @Mock
        private EventPublisher eventPublisher;

        @InjectMocks
        private ConfigService configService;

        private static final String TENANT_ID = "test-tenant-001";

        @BeforeEach
        void setUp() {
                TenantContext.setTenantId(TENANT_ID);
        }

        @AfterEach
        void tearDown() {
                TenantContext.clear();
        }

        @Test
        @DisplayName("resolveConfig returns tenant-specific override when available")
        void resolveConfig_returnsTenanOverride() {
                SystemConfig tenantConfig = new SystemConfig();
                tenantConfig.setTenantId(TENANT_ID);
                tenantConfig.setConfigKey("smtp.provider");
                tenantConfig.setConfigValue("ses");

                ConfigDto.Response expected = ConfigDto.Response.builder()
                                .configKey("smtp.provider")
                                .configValue("ses")
                                .tenantId(TENANT_ID)
                                .build();

                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.empty());
                when(configRepository.findByKeyWithFallback("smtp.provider", TENANT_ID))
                                .thenReturn(List.of(tenantConfig));
                when(configMapper.toResponse(tenantConfig)).thenReturn(expected);

                ConfigDto.Response result = configService.resolveConfig("smtp.provider");

                assertThat(result.getConfigValue()).isEqualTo("ses");
                assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
                verify(cacheService).set(anyString(), eq(expected), any());
        }

        @Test
        @DisplayName("resolveConfig returns cached value when available")
        void resolveConfig_returnsCachedValue() {
                ConfigDto.Response cached = ConfigDto.Response.builder()
                                .configKey("smtp.provider")
                                .configValue("postal")
                                .build();

                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.of(cached));

                ConfigDto.Response result = configService.resolveConfig("smtp.provider");

                assertThat(result.getConfigValue()).isEqualTo("postal");
                verify(configRepository, never()).findByKeyWithFallback(any(), any());
        }

        @Test
        @DisplayName("resolveConfig throws NotFoundException when key not found")
        void resolveConfig_throwsNotFound() {
                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.empty());
                when(configRepository.findByKeyWithFallback("nonexistent", TENANT_ID))
                                .thenReturn(List.of());

                assertThatThrownBy(() -> configService.resolveConfig("nonexistent"))
                                .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("createConfig fails when key already exists")
        void createConfig_throwsConflict() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("existing.key")
                                .configValue("value")
                                .build();

                when(configRepository.existsByTenantIdAndConfigKeyAndDeletedAtIsNull(TENANT_ID, "existing.key"))
                                .thenReturn(true);

                assertThatThrownBy(() -> configService.createConfig(TENANT_ID, request))
                                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("createConfig saves and publishes event on success")
        void createConfig_success() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("new.key")
                                .configValue("new.value")
                                .build();

                SystemConfig entity = new SystemConfig();
                entity.setConfigKey("new.key");
                entity.setConfigValue("new.value");

                ConfigDto.Response expected = ConfigDto.Response.builder()
                                .configKey("new.key")
                                .configValue("new.value")
                                .build();

                when(configRepository.existsByTenantIdAndConfigKeyAndDeletedAtIsNull(TENANT_ID, "new.key"))
                                .thenReturn(false);
                when(configMapper.toEntity(request)).thenReturn(entity);
                when(configRepository.save(entity)).thenReturn(entity);
                when(configMapper.toResponse(entity)).thenReturn(expected);

                ConfigDto.Response result = configService.createConfig(TENANT_ID, request);

                assertThat(result.getConfigKey()).isEqualTo("new.key");
                verify(eventPublisher).publish(anyString(), any());
                verify(cacheService).delete(anyString());
        }
}
