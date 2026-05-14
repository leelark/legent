package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.PublicContent;
import com.legent.foundation.dto.PublicContentDto;
import com.legent.foundation.repository.PublicContentRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicContentServiceTest {

    @Mock
    private PublicContentRepository repository;

    private PublicContentService service;

    @BeforeEach
    void setUp() {
        service = new PublicContentService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsertById_deniesCrossWorkspaceRecord() {
        when(repository.findByIdAndTenantIdAndWorkspaceId("content-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        PublicContentDto.UpsertRequest request = new PublicContentDto.UpsertRequest();
        request.setContentType("PAGE");
        request.setPageKey("home");
        request.setTitle("Home");
        request.setPayload(Map.of("heroTitle", "Home"));

        assertThatThrownBy(() -> service.upsert(request, "content-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Public content not found");

        verify(repository, never()).save(any(PublicContent.class));
    }

    @Test
    void publish_deniesCrossWorkspaceRecord() {
        when(repository.findByIdAndTenantIdAndWorkspaceId("content-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish("content-1", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Public content not found");

        verify(repository, never()).save(any(PublicContent.class));
    }
}
