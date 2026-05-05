package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.mapper.SegmentMapper;
import com.legent.audience.repository.SegmentRepository;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor

public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentMapper segmentMapper;
    private final SegmentEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<SegmentDto.Response> list(Pageable pageable) {
        return segmentRepository.findAllByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId(), pageable)
                .map(segmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SegmentDto.Response getById(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment segment = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        return segmentMapper.toResponse(segment);
    }

    @Transactional
    public SegmentDto.Response create(SegmentDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        if (segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("Segment", "name", request.getName());
        }

        Segment entity = segmentMapper.toEntity(request);
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        if (request.getSegmentType() != null) {
            entity.setSegmentType(parseSegmentType(request.getSegmentType()));
        }
        normalizeForPersist(entity);

        Segment saved = segmentRepository.save(entity);
        log.info("Segment created: name={}, id={}", saved.getName(), saved.getId());
        eventPublisher.publishCreated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public SegmentDto.Response update(String id, SegmentDto.UpdateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment existing = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getRules() != null) existing.setRules(request.getRules());
        if (request.getScheduleEnabled() != null) existing.setScheduleEnabled(request.getScheduleEnabled());
        if (request.getStatus() != null) existing.setStatus(Segment.SegmentStatus.valueOf(request.getStatus().toUpperCase()));
        normalizeForPersist(existing);

        Segment saved = segmentRepository.save(existing);
        eventPublisher.publishUpdated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment existing = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        existing.softDelete();
        segmentRepository.save(existing);
        log.info("Segment deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return segmentRepository.countByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId());
    }

    private Segment.SegmentType parseSegmentType(String segmentType) {
        if (segmentType == null || segmentType.isBlank()) {
            return Segment.SegmentType.FILTER;
        }
        String normalized = segmentType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FILTER", "DYNAMIC", "DYNAMIC_LIST" -> Segment.SegmentType.FILTER;
            case "MANUAL", "STATIC", "STATIC_LIST" -> Segment.SegmentType.MANUAL;
            case "QUERY" -> Segment.SegmentType.QUERY;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid segmentType: " + segmentType + ". Allowed values: FILTER, QUERY, MANUAL, DYNAMIC, STATIC");
        };
    }

    private void normalizeForPersist(Segment segment) {
        if (segment.getOwnershipScope() == null || segment.getOwnershipScope().isBlank()) {
            segment.setOwnershipScope("WORKSPACE");
        }
        if (segment.getRules() == null) {
            segment.setRules(defaultRules());
        }
        if (segment.getTags() == null) {
            segment.setTags(new ArrayList<>());
        }
        if (segment.getStatus() == null) {
            segment.setStatus(Segment.SegmentStatus.DRAFT);
        }
        if (segment.getSegmentType() == null) {
            segment.setSegmentType(Segment.SegmentType.FILTER);
        }
    }

    private LinkedHashMap<String, Object> defaultRules() {
        LinkedHashMap<String, Object> rules = new LinkedHashMap<>();
        rules.put("operator", "AND");
        rules.put("conditions", new ArrayList<>());
        rules.put("groups", new ArrayList<>());
        return rules;
    }
}
