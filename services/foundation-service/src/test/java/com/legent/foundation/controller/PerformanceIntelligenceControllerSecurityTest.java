package com.legent.foundation.controller;

import com.legent.foundation.dto.performance.AiGenerationPreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceIntelligenceControllerSecurityTest {

    @Test
    void aiSegmentPreviewRequiresAudienceWritePermission() throws NoSuchMethodException {
        PreAuthorize preAuthorize = PerformanceIntelligenceController.class
                .getMethod("previewAiSegmentGeneration", AiGenerationPreviewRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("audience:write");
        assertThat(preAuthorize.value()).doesNotContain("tenant:*");
    }

    @Test
    void aiWorkflowPreviewRequiresWorkflowWritePermission() throws NoSuchMethodException {
        PreAuthorize preAuthorize = PerformanceIntelligenceController.class
                .getMethod("previewAiWorkflowGeneration", AiGenerationPreviewRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("workflow:write");
        assertThat(preAuthorize.value()).doesNotContain("tenant:*");
    }
}
