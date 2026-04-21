package com.legent.foundation.service;

import com.legent.foundation.domain.AuditLog;
import com.legent.foundation.repository.AuditLogRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String resourceType, String resourceId, Map<String, Object> changes) {
        log(action, resourceType, resourceId, changes, "SUCCESS", null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String resourceType, String resourceId, Map<String, Object> changes, String status, String error) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTenantId(TenantContext.getTenantId());
            auditLog.setUserId(TenantContext.getUserId());
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setChanges(changes);
            auditLog.setStatus(status);
            auditLog.setErrorMessage(error);
            
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}", action, e);
        }
    }
}
