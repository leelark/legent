package com.legent.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.TemplateTestSendRecord;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.TemplateTestSendRecordRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateTestSendService {

    private final TemplateTestSendRecordRepository recordRepository;
    private final EmailRenderService renderService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public TemplateTestSendRecord send(String tenantId, String templateId, EmailStudioDto.TestSendRequest request) {
        return send(tenantId, TenantContext.requireWorkspaceId(), templateId, request);
    }

    public TemplateTestSendRecord send(String tenantId, String workspaceId, String templateId, EmailStudioDto.TestSendRequest request) {
        EmailStudioDto.RenderRequest renderRequest = new EmailStudioDto.RenderRequest();
        renderRequest.setVariables(request.getVariables());
        renderRequest.setBrandKitId(request.getBrandKitId());
        renderRequest.setPublishedOnly(false);
        EmailStudioDto.RenderResponse rendered = renderService.render(tenantId, workspaceId, templateId, renderRequest);

        TemplateTestSendRecord record = new TemplateTestSendRecord();
        record.setTenantId(tenantId);
        record.setWorkspaceId(workspaceId);
        record.setTemplateId(templateId);
        record.setRecipientEmail(request.getEmail());
        record.setRecipientGroup(request.getRecipientGroup());
        record.setSubject(request.getSubjectOverride() != null && !request.getSubjectOverride().isBlank()
                ? request.getSubjectOverride()
                : rendered.getSubject());
        record.setVariablesJson(writeJson(request.getVariables()));

        if (!rendered.getErrors().isEmpty()) {
            record.setStatus("FAILED");
            record.setErrorMessage(String.join("; ", rendered.getErrors()));
            TemplateTestSendRecord saved = recordRepository.save(record);
            throw new ValidationException("template", "Test send blocked because render validation failed: " + saved.getErrorMessage());
        }

        String messageId = "tpl-test-" + templateId + "-" + Instant.now().toEpochMilli();
        Map<String, Object> payload = Map.of(
                "email", request.getEmail(),
                "subscriberId", "template-test",
                "campaignId", "template-preview",
                "messageId", messageId,
                "subject", record.getSubject(),
                "htmlBody", rendered.getHtmlContent(),
                "textBody", rendered.getTextContent()
        );
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                tenantId,
                "content-service",
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);

        record.setStatus("QUEUED");
        record.setMessageId(messageId);
        return recordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<TemplateTestSendRecord> list(String tenantId, String templateId) {
        return list(tenantId, TenantContext.requireWorkspaceId(), templateId);
    }

    @Transactional(readOnly = true)
    public List<TemplateTestSendRecord> list(String tenantId, String workspaceId, String templateId) {
        return recordRepository.findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, workspaceId, templateId);
    }

    private String writeJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
