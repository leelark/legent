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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void getPendingApprovals_usesDefaultFirstPageRequestWithinTenantWorkspace() {
        TemplateApproval approval = approval("approval-1", TemplateApproval.ApprovalStatus.PENDING);
        when(approvalRepository.findByTenantIdAndWorkspaceIdAndStatusOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(TemplateApproval.ApprovalStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(approval));

        List<TemplateApproval> approvals = service.getPendingApprovals("tenant-1", "workspace-1");

        assertThat(approvals).containsExactly(approval);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRepository).findByTenantIdAndWorkspaceIdAndStatusOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(TemplateApproval.ApprovalStatus.PENDING),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getPendingApprovals_clampsOversizedLimitToMaxFirstPageRequest() {
        when(approvalRepository.findByTenantIdAndWorkspaceIdAndStatusOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(TemplateApproval.ApprovalStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.getPendingApprovals("tenant-1", "workspace-1", 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRepository).findByTenantIdAndWorkspaceIdAndStatusOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(TemplateApproval.ApprovalStatus.PENDING),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void getTemplateApprovalHistory_usesDefaultFirstPageRequestAfterTemplateScopeCheck() {
        EmailTemplate template = template("template-1");
        TemplateApproval approval = approval("approval-1", TemplateApproval.ApprovalStatus.APPROVED);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(approvalRepository.findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                any(Pageable.class)))
                .thenReturn(List.of(approval));

        List<TemplateApproval> approvals = service.getTemplateApprovalHistory("tenant-1", "workspace-1", "template-1");

        assertThat(approvals).containsExactly(approval);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRepository).findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getTemplateApprovalHistory_clampsOversizedLimitToMaxFirstPageRequest() {
        EmailTemplate template = template("template-1");
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(approvalRepository.findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.getTemplateApprovalHistory("tenant-1", "workspace-1", "template-1", 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(approvalRepository).findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("template-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void approveTemplate_rejectsNonPendingApprovalWithoutStateChanges() {
        TemplateApproval approval = approval("approval-1", TemplateApproval.ApprovalStatus.APPROVED);
        when(approvalRepository.findByIdAndTenantIdAndWorkspaceId("approval-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approveTemplate("tenant-1", "workspace-1", "approval-1", "approved"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PENDING");

        verify(templateRepository, never()).findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(any(), any(), any());
        verify(approvalRepository, never()).saveAndFlush(any(TemplateApproval.class));
        verify(templateRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submitForApproval_publishesWorkspaceScopedEvent() {
        EmailTemplate template = template("template-1");
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(approvalRepository.findPendingApproval("tenant-1", "workspace-1", "template-1"))
                .thenReturn(Optional.empty());
        when(versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());
        when(approvalRepository.saveAndFlush(any(TemplateApproval.class))).thenAnswer(invocation -> {
            TemplateApproval approval = invocation.getArgument(0);
            approval.setId("approval-1");
            return approval;
        });
        when(approvalRepository.findById("approval-1")).thenReturn(Optional.empty());

        service.submitForApproval("tenant-1", "workspace-1", "template-1", "ready");

        verify(eventPublisher).publishTemplateSubmittedForApproval(
                "tenant-1", "workspace-1", "template-1", "Template", 1);
    }

    @Test
    void approveTemplate_publishesWorkspaceScopedEvent() {
        EmailTemplate template = template("template-1");
        TemplateApproval approval = approval("approval-1", TemplateApproval.ApprovalStatus.PENDING);
        when(approvalRepository.findByIdAndTenantIdAndWorkspaceId("approval-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(approval));
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(approvalRepository.saveAndFlush(approval)).thenReturn(approval);
        when(approvalRepository.findById("approval-1")).thenReturn(Optional.of(approval));

        service.approveTemplate("tenant-1", "workspace-1", "approval-1", "approved");

        verify(eventPublisher).publishTemplateApproved(
                "tenant-1", "workspace-1", "template-1", "Template", 1, "reviewer-1");
    }

    @Test
    void rejectTemplate_publishesWorkspaceScopedEvent() {
        EmailTemplate template = template("template-1");
        TemplateApproval approval = approval("approval-1", TemplateApproval.ApprovalStatus.PENDING);
        when(approvalRepository.findByIdAndTenantIdAndWorkspaceId("approval-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(approval));
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(approvalRepository.saveAndFlush(approval)).thenReturn(approval);
        when(approvalRepository.findById("approval-1")).thenReturn(Optional.of(approval));

        service.rejectTemplate("tenant-1", "workspace-1", "approval-1", "needs edits");

        verify(eventPublisher).publishTemplateRejected(
                "tenant-1", "workspace-1", "template-1", "Template", 1, "needs edits", "reviewer-1");
    }

    @Test
    void publishTemplate_usesScopedWorkspaceForVersionPublish() {
        EmailTemplate template = template("template-1");
        template.setStatus(EmailTemplate.TemplateStatus.APPROVED);
        TemplateVersion version = new TemplateVersion();
        version.setVersionNumber(2);
        version.setTemplate(template);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId("template-1", 2, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(version));
        when(approvalRepository.hasApprovedApproval("tenant-1", "workspace-1", "template-1", 2)).thenReturn(true);

        service.publishTemplate("tenant-1", "workspace-1", "template-1", 2, false, null);

        verify(renderService).requirePublishable("tenant-1", "workspace-1", "template-1", 2);
        verify(versionService).publishVersion("tenant-1", "workspace-1", "template-1", 2);
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

    private TemplateApproval approval(String id, TemplateApproval.ApprovalStatus status) {
        TemplateApproval approval = new TemplateApproval();
        approval.setId(id);
        approval.setTenantId("tenant-1");
        approval.setWorkspaceId("workspace-1");
        approval.setTemplateId("template-1");
        approval.setVersionNumber(1);
        approval.setRequestedBy("requester-1");
        approval.setStatus(status);
        return approval;
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
