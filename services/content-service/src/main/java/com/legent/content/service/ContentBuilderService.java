package com.legent.content.service;

import com.legent.common.exception.ValidationException;
import com.legent.content.domain.TemplateTestSendRecord;
import com.legent.content.dto.EmailStudioDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContentBuilderService {

    private static final int MAX_BLOCKS = 200;
    private static final int MAX_MATRIX_RECIPIENTS = 50;

    private final EmailContentValidationService validationService;
    private final TemplateTestSendService testSendService;

    public EmailStudioDto.BuilderLayoutResponse renderLayout(EmailStudioDto.BuilderLayoutRequest request) {
        if (request == null || request.getBlocks() == null || request.getBlocks().isEmpty()) {
            throw new ValidationException("blocks", "At least one content block is required");
        }
        if (request.getBlocks().size() > MAX_BLOCKS) {
            throw new ValidationException("blocks", "A template can contain at most " + MAX_BLOCKS + " blocks");
        }

        List<String> blockIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder html = new StringBuilder();
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tbody>");
        for (EmailStudioDto.BuilderBlock block : request.getBlocks()) {
            validateBlock(block, blockIds);
            String content = validationService.sanitize(applyVariables(block.getContent(), request.getVariables()));
            Map<String, Object> styles = block.getStyles() == null ? Map.of() : block.getStyles();
            html.append("<tr><td style=\"")
                    .append(style("padding", number(styles.get("padding"), 16) + "px"))
                    .append(style("background", cssColor(styles.get("backgroundColor"), "#ffffff")))
                    .append(style("color", cssColor(styles.get("textColor"), "#0f172a")))
                    .append(style("border-radius", number(styles.get("borderRadius"), 0) + "px"))
                    .append("\">")
                    .append(content)
                    .append("</td></tr>");
            if (Boolean.TRUE.equals(valueAsBoolean(block.getSettings(), "hideOnMobile"))) {
                warnings.add("Block " + block.getId() + " uses hideOnMobile; verify responsive fallback.");
            }
        }
        html.append("</tbody></table>");

        EmailStudioDto.ValidationResponse validation = validationService.validate(html.toString(), null);
        EmailStudioDto.BuilderLayoutResponse response = new EmailStudioDto.BuilderLayoutResponse();
        response.setSubject(applyVariables(request.getSubject(), request.getVariables()));
        response.setHtmlContent(validation.getSanitizedHtml());
        response.setTextContent(validation.getTextContent());
        response.setBlockCount(request.getBlocks().size());
        response.setBlockIds(blockIds);
        response.setWarnings(warnings);
        response.setValidation(validation);
        return response;
    }

    public EmailStudioDto.TestSendMatrixResponse sendMatrix(String tenantId,
                                                            String templateId,
                                                            EmailStudioDto.TestSendMatrixRequest request) {
        if (request == null || request.getRecipients() == null || request.getRecipients().isEmpty()) {
            throw new ValidationException("recipients", "At least one test recipient is required");
        }
        if (request.getRecipients().size() > MAX_MATRIX_RECIPIENTS) {
            throw new ValidationException("recipients", "Test matrix can contain at most " + MAX_MATRIX_RECIPIENTS + " recipients");
        }

        List<EmailStudioDto.TestSendRecordResponse> records = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int queued = 0;
        for (EmailStudioDto.TestSendMatrixRecipient recipient : request.getRecipients()) {
            try {
                EmailStudioDto.TestSendRequest testSend = new EmailStudioDto.TestSendRequest();
                testSend.setEmail(recipient.getEmail());
                testSend.setRecipientGroup(recipient.getRecipientGroup());
                testSend.setSubjectOverride(request.getSubjectOverride());
                testSend.setBrandKitId(recipient.getBrandKitId());
                testSend.setVariables(recipient.getVariables());
                TemplateTestSendRecord record = testSendService.send(tenantId, templateId, testSend);
                records.add(mapRecord(record));
                queued++;
            } catch (RuntimeException ex) {
                errors.add(recipient.getEmail() + ": " + ex.getMessage());
            }
        }

        EmailStudioDto.TestSendMatrixResponse response = new EmailStudioDto.TestSendMatrixResponse();
        response.setMatrixName(request.getMatrixName());
        response.setRequested(request.getRecipients().size());
        response.setQueued(queued);
        response.setFailed(errors.size());
        response.setRecords(records);
        response.setErrors(errors);
        return response;
    }

    private void validateBlock(EmailStudioDto.BuilderBlock block, List<String> seenIds) {
        if (block == null || block.getId() == null || block.getId().isBlank()) {
            throw new ValidationException("blocks.id", "Each builder block needs a stable id");
        }
        if (seenIds.contains(block.getId())) {
            throw new ValidationException("blocks.id", "Duplicate block id: " + block.getId());
        }
        seenIds.add(block.getId());
        if (block.getBlockType() == null || block.getBlockType().isBlank()) {
            throw new ValidationException("blocks.blockType", "Block type is required");
        }
        if (block.getContent() == null || block.getContent().isBlank()) {
            throw new ValidationException("blocks.content", "Block content is required");
        }
    }

    private String applyVariables(String value, Map<String, Object> variables) {
        if (value == null || variables == null || variables.isEmpty()) {
            return value == null ? "" : value;
        }
        String rendered = value;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private String style(String key, String value) {
        return key + ":" + value + ";";
    }

    private int number(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Math.max(0, Math.min(96, Integer.parseInt(String.valueOf(value))));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String cssColor(Object value, String defaultValue) {
        String raw = value == null ? defaultValue : String.valueOf(value);
        return raw.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$") ? raw : defaultValue;
    }

    private boolean valueAsBoolean(Map<String, Object> map, String key) {
        if (map == null) {
            return false;
        }
        Object value = map.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private EmailStudioDto.TestSendRecordResponse mapRecord(TemplateTestSendRecord record) {
        EmailStudioDto.TestSendRecordResponse response = new EmailStudioDto.TestSendRecordResponse();
        response.setId(record.getId());
        response.setTemplateId(record.getTemplateId());
        response.setRecipientEmail(record.getRecipientEmail());
        response.setRecipientGroup(record.getRecipientGroup());
        response.setSubject(record.getSubject());
        response.setStatus(record.getStatus());
        response.setMessageId(record.getMessageId());
        response.setVariablesJson(record.getVariablesJson());
        response.setErrorMessage(record.getErrorMessage());
        response.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        response.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null);
        return response;
    }
}
