package com.legent.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateApproval;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateWorkflowDto;
import com.legent.content.event.ContentEventPublisher;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateApprovalRepository;
import com.legent.content.repository.TemplateVersionRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateWorkflowServiceTest {

    @Mock private EmailTemplateRepository templateRepository;
    @Mock private TemplateApprovalRepository approvalRepository;
    @Mock private TemplateVersionRepository versionRepository;
    @Mock private TemplateVersionService versionService;
    @Mock private EmailRenderService renderService;
    @Mock private ContentEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private TemplateWorkflowService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new TemplateWorkflowService(
                templateRepository,
                approvalRepository,
                versionRepository,
                versionService,
                renderService,
                eventPublisher,
                new AiContentAssistanceMetadataSupport(objectMapper));
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("reviewer-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveDraft_withApprovedAiEvidence_storesHashOnlyMetadataAndDraftContent() throws Exception {
        EmailTemplate template = template("template-1");
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(templateRepository.save(template)).thenReturn(template);

        service.saveDraft(
                "tenant-1",
                "workspace-1",
                "template-1",
                "Generated subject",
                "<p>Generated body</p>",
                "Generated body",
                approvedAiApplication());

        assertThat(template.getSubject()).isEqualTo("Generated subject");
        assertThat(template.getHtmlContent()).isEqualTo("<p>Generated body</p>");
        Map<String, Object> metadata = objectMapper.readValue(template.getMetadata(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = (Map<String, Object>) metadata.get("aiContentAssistance");
        assertThat(ai).containsEntry("status", "APPLIED_TO_DRAFT")
                .containsEntry("decision", "APPROVED_DRAFT_ONLY")
                .containsEntry("auditId", "audit-1")
                .containsEntry("outputHash", "b".repeat(64))
                .containsEntry("humanReviewed", true)
                .containsEntry("appliedBy", "reviewer-1");
        assertThat(template.getMetadata()).doesNotContain("Generated subject", "Generated body", "<p>Generated body</p>");
        verify(templateRepository).save(template);
    }

    @Test
    void saveDraft_rejectsUnreviewedAiEvidenceBeforePersisting() {
        EmailTemplate template = template("template-1");
        TemplateWorkflowDto.AiDraftApplication application = approvedAiApplication();
        application.setHumanReviewed(false);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.saveDraft(
                "tenant-1",
                "workspace-1",
                "template-1",
                "Generated subject",
                "<p>Generated body</p>",
                "Generated body",
                application))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("human review");

        verify(templateRepository, never()).save(any());
    }

    @Test
    void saveDraft_rejectsMalformedAiEvidenceBeforePersisting() {
        EmailTemplate template = template("template-1");
        TemplateWorkflowDto.AiDraftApplication application = approvedAiApplication();
        application.setOutputHash("raw-output-not-a-hash");
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.saveDraft(
                "tenant-1",
                "workspace-1",
                "template-1",
                "Generated subject",
                "<p>Generated body</p>",
                "Generated body",
                application))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("output hash");

        verify(templateRepository, never()).save(any());
    }

    @Test
    void publishTemplate_blocksUnresolvedAiMetadataBeforeRenderOrPublish() {
        EmailTemplate template = template("template-1");
        template.setStatus(EmailTemplate.TemplateStatus.APPROVED);
        template.setMetadata("{\"aiContentAssistance\":{\"decision\":\"DENIED\",\"status\":\"DENIED\",\"humanReviewed\":false}}");
        TemplateVersion version = new TemplateVersion();
        version.setVersionNumber(1);
        version.setTemplate(template);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId("template-1", 1, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(version));
        when(approvalRepository.hasApprovedApproval("tenant-1", "workspace-1", "template-1", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.publishTemplate("tenant-1", "workspace-1", "template-1", 1, false, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("AI content assistance evidence");

        verifyNoInteractions(renderService);
        verify(versionService, never()).publishVersion(any(), any());
    }

    private EmailTemplate template(String id) {
        EmailTemplate template = new EmailTemplate();
        template.setId(id);
        template.setTenantId("tenant-1");
        template.setWorkspaceId("workspace-1");
        template.setName("Template");
        template.setSubject("Original");
        template.setHtmlContent("<p>Original</p>");
        template.setTextContent("Original");
        template.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        template.setCreatedBy("creator-1");
        template.setMetadata("{}");
        return template;
    }

    private TemplateWorkflowDto.AiDraftApplication approvedAiApplication() {
        TemplateWorkflowDto.AiDraftApplication application = new TemplateWorkflowDto.AiDraftApplication();
        application.setAuditId("audit-1");
        application.setPolicyKey("draft-content");
        application.setPolicyVersion("v3");
        application.setDecision("APPROVED_DRAFT_ONLY");
        application.setRequestedAction("APPLY_TO_DRAFT");
        application.setPromptHash("a".repeat(64));
        application.setOutputHash("b".repeat(64));
        application.setHumanReviewed(true);
        application.setEvidenceRefs(List.of("foundation-audit://audit-1"));
        return application;
    }
}
