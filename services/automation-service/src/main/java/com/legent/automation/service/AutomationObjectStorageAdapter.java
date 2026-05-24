package com.legent.automation.service;

import com.legent.automation.domain.AutomationArtifact;

import java.time.Instant;

public interface AutomationObjectStorageAdapter {

    MovementResult copyArtifact(AutomationArtifact sourceArtifact,
                                AutomationArtifact targetArtifact,
                                MovementRequest request);

    record MovementRequest(String tenantId,
                           String workspaceId,
                           String activityId,
                           String runId,
                           String activityType,
                           String operation) {
    }

    record MovementResult(String operation,
                          String sourceArtifactId,
                          String targetArtifactId,
                          long bytesMoved,
                          String sha256,
                          String contentType,
                          Instant completedAt) {
    }
}
