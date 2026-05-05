package com.legent.audience.service;

import java.util.Optional;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.event.SubscriberEventPublisher;
import com.legent.audience.mapper.SubscriberMapper;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.cache.service.CacheService;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriberService Unit Tests")

class SubscriberServiceTest {

    @Mock private SubscriberRepository subscriberRepository;
    @Mock private SubscriberMapper subscriberMapper;
    @Mock private CacheService cacheService;
    @Mock private SubscriberEventPublisher eventPublisher;
    @InjectMocks private SubscriberService subscriberService;

    private static final String TENANT_ID = "tenant-001";
    private static final String WORKSPACE_ID = "ws-001";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    @Test
    @DisplayName("create succeeds with unique subscriber key")
    void create_success() {
        SubscriberDto.CreateRequest req = SubscriberDto.CreateRequest.builder()
                .subscriberKey("sub-001").email("test@example.com").firstName("John").build();

        Subscriber entity = new Subscriber();
        entity.setSubscriberKey("sub-001");
        entity.setEmail("test@example.com");

        SubscriberDto.Response expected = SubscriberDto.Response.builder()
                .subscriberKey("sub-001").email("test@example.com").build();

        when(subscriberRepository.existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(TENANT_ID, "sub-001"))
                .thenReturn(false);
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "test@example.com"))
                .thenReturn(Optional.empty());
        when(subscriberMapper.toEntity(req)).thenReturn(entity);
        when(subscriberRepository.save(entity)).thenReturn(entity);
        when(subscriberMapper.toResponse(entity)).thenReturn(expected);

        SubscriberDto.Response result = subscriberService.create(req);

        assertThat(result.getSubscriberKey()).isEqualTo("sub-001");
        verify(eventPublisher).publishCreated(entity);
    }

    @Test
    @DisplayName("create throws ConflictException for duplicate subscriber key")
    void create_conflict() {
        SubscriberDto.CreateRequest req = SubscriberDto.CreateRequest.builder()
                .subscriberKey("existing").email("test@example.com").build();

        when(subscriberRepository.existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(TENANT_ID, "existing"))
                .thenReturn(true);

        assertThatThrownBy(() -> subscriberService.create(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("getById returns cached subscriber when available")
    void getById_cached() {
        SubscriberDto.Response cached = SubscriberDto.Response.builder()
                .id("id-1").subscriberKey("sub-001").build();

        when(cacheService.get(anyString(), eq(SubscriberDto.Response.class)))
                .thenReturn(Optional.of(cached));

        SubscriberDto.Response result = subscriberService.getById("id-1");

        assertThat(result.getSubscriberKey()).isEqualTo("sub-001");
        verify(subscriberRepository, never()).findById(any());
    }

    @Test
    @DisplayName("delete soft-deletes and publishes event")
    void delete_success() {
        Subscriber entity = new Subscriber();
        entity.setTenantId(TENANT_ID);
        entity.setWorkspaceId(WORKSPACE_ID);
        entity.setSubscriberKey("sub-001");

        when(subscriberRepository.findById("id-1")).thenReturn(Optional.of(entity));
        when(subscriberRepository.save(entity)).thenReturn(entity);

        subscriberService.delete("id-1");

        assertThat(entity.isDeleted()).isTrue();
        verify(eventPublisher).publishDeleted(entity);
    }

    @Test
    @DisplayName("getById throws NotFoundException for missing subscriber")
    void getById_notFound() {
        when(cacheService.get(anyString(), eq(SubscriberDto.Response.class)))
                .thenReturn(Optional.empty());
        when(subscriberRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriberService.getById("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
