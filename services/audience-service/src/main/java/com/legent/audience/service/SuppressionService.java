package com.legent.audience.service;

import java.util.ArrayList;

import java.util.List;

import com.legent.audience.domain.Suppression;
import com.legent.audience.dto.SuppressionDto;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor

public class SuppressionService {

    private final SuppressionRepository suppressionRepository;

    @Transactional(readOnly = true)
    public Page<SuppressionDto.Response> list(Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return suppressionRepository.findAllByTenantAndWorkspace(tenantId, workspaceId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public SuppressionDto.Response create(SuppressionDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Suppression.SuppressionType type = Suppression.SuppressionType.valueOf(request.getSuppressionType().toUpperCase());
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(tenantId, workspaceId, normalizedEmail, type)) {
            throw new ConflictException("Suppression", "email+type", normalizedEmail + ":" + request.getSuppressionType());
        }

        Suppression entity = new Suppression();
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        entity.setEmail(normalizedEmail);
        entity.setSuppressionType(type);
        entity.setReason(request.getReason());
        entity.setSource(request.getSource());
        if (request.getExpiresAt() != null) entity.setExpiresAt(request.getExpiresAt());

        Suppression saved = suppressionRepository.save(entity);
        log.info("Suppression created: email={}, type={}", saved.getEmail(), saved.getSuppressionType());
        return toResponse(saved);
    }

    @Transactional
    public List<SuppressionDto.Response> bulkCreate(SuppressionDto.BulkRequest request) {
        List<SuppressionDto.Response> results = new ArrayList<>();
        for (SuppressionDto.CreateRequest item : request.getSuppressions()) {
            try {
                results.add(create(item));
            } catch (ConflictException e) {
                log.debug("Suppression already exists: {}", item.getEmail());
            }
        }
        log.info("Bulk suppression: {} added", results.size());
        return results;
    }

    /**
     * Compliance check — used at send-time by the delivery service.
     */
    @Transactional(readOnly = true)
    public SuppressionDto.ComplianceCheck checkCompliance(String email) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        List<Suppression> active = suppressionRepository.findActiveSuppression(tenantId, workspaceId, email.toLowerCase().trim());

        if (active.isEmpty()) {
            return SuppressionDto.ComplianceCheck.builder()
                    .email(email).suppressed(false).build();
        }

        Suppression first = active.get(0);
        return SuppressionDto.ComplianceCheck.builder()
                .email(email).suppressed(true)
                .suppressionType(first.getSuppressionType().name())
                .reason(first.getReason()).build();
    }

    @Transactional
    public void delete(String id) {
        Suppression existing = suppressionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suppression", id));
        existing.softDelete();
        suppressionRepository.save(existing);
        log.info("Suppression deleted: id={}", id);
    }

    private SuppressionDto.Response toResponse(Suppression entity) {
        return SuppressionDto.Response.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .suppressionType(entity.getSuppressionType().name())
                .reason(entity.getReason())
                .source(entity.getSource())
                .suppressedAt(entity.getSuppressedAt())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
