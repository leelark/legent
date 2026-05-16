package com.legent.common.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailContentReference {
    private String referenceId;
    private String storageBackend;
    private String tenantId;
    private String workspaceId;
    private String campaignId;
    private String jobId;
    private String batchId;
    private String messageId;
    private String contentId;
    private String subjectSha256;
    private String htmlSha256;
    private String textSha256;
    private Integer subjectBytes;
    private Integer htmlBytes;
    private Integer textBytes;
    private Instant createdAt;
    private Instant expiresAt;
    private Boolean inlineFallbackIncluded;
}
