package com.legent.delivery.service;

import com.legent.delivery.domain.SuppressionSignal;
import com.legent.delivery.repository.SuppressionSignalRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuppressionSignalService {

    private final SuppressionSignalRepository repository;

    @Transactional
    public SuppressionSignal recordSignal(String tenantId,
                                          String workspaceId,
                                          String email,
                                          SuppressionSignal.SignalType type,
                                          String reason,
                                          String sourceMessageId) {
        String scopedTenantId = require(tenantId, "tenantId");
        String scopedWorkspaceId = require(workspaceId, "workspaceId");
        String normalizedEmail = normalizeEmail(email);
        if (type == null) {
            throw new IllegalArgumentException("suppression signal type is required");
        }
        String typeName = type.name();

        return repository
                .findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndTypeAndDeletedAtIsNull(
                        scopedTenantId,
                        scopedWorkspaceId,
                        normalizedEmail,
                        typeName)
                .orElseGet(() -> repository.save(signal(
                        scopedTenantId,
                        scopedWorkspaceId,
                        normalizedEmail,
                        typeName,
                        reason,
                        sourceMessageId)));
    }

    private SuppressionSignal signal(String tenantId,
                                     String workspaceId,
                                     String email,
                                     String type,
                                     String reason,
                                     String sourceMessageId) {
        SuppressionSignal signal = new SuppressionSignal();
        signal.setTenantId(tenantId);
        signal.setWorkspaceId(workspaceId);
        signal.setOwnershipScope("WORKSPACE");
        signal.setEmail(email);
        signal.setType(type);
        signal.setReason(trimToNull(reason));
        signal.setSourceMessageId(trimToNull(sourceMessageId));
        return signal;
    }

    private String require(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required for suppression signal");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.contains("@")) {
            throw new IllegalArgumentException("email is required for suppression signal");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
