package com.legent.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateTestSendRecord;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateTestSendRecordRepository;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateTestSendServiceTest {

    @Mock private TemplateTestSendRecordRepository recordRepository;
    @Mock private EmailTemplateRepository templateRepository;
    @Mock private EmailRenderService renderService;
    @Mock private EventPublisher eventPublisher;

    private TemplateTestSendService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new TemplateTestSendService(
                recordRepository,
                templateRepository,
                renderService,
                eventPublisher,
                objectMapper,
                new AiContentAssistanceMetadataSupport(objectMapper));
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listUsesDefaultFirstPageRequest() {
        TemplateTestSendRecord record = new TemplateTestSendRecord();
        record.setId("test-send-1");
        when(recordRepository.findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                any(Pageable.class)))
                .thenReturn(List.of(record));

        List<TemplateTestSendRecord> records = service.list("tenant-1", "workspace-1", "template-1");

        assertThat(records).containsExactly(record);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recordRepository).findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void listClampsOversizedLimitToMaxFirstPageRequest() {
        when(recordRepository.findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.list("tenant-1", "workspace-1", "template-1", 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recordRepository).findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void send_blocksUnresolvedAiMetadataBeforeRenderOrPublish() {
        EmailTemplate template = new EmailTemplate();
        template.setId("template-1");
        template.setTenantId("tenant-1");
        template.setWorkspaceId("workspace-1");
        template.setMetadata("{\"aiContentAssistance\":{\"decision\":\"REVIEW_REQUIRED\",\"status\":\"PENDING_REVIEW\",\"humanReviewed\":false}}");
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));

        EmailStudioDto.TestSendRequest request = new EmailStudioDto.TestSendRequest();
        request.setEmail("reviewer@example.com");

        assertThatThrownBy(() -> service.send("tenant-1", "workspace-1", "template-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("AI content assistance evidence");

        verifyNoInteractions(renderService, eventPublisher, recordRepository);
    }
}
