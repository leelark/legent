package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.mapper.SegmentMapper;
import com.legent.audience.repository.SegmentRepository;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentMapper segmentMapper;
    private final SegmentEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<SegmentDto.Response> list(Pageable pageable) {
        return segmentRepository.findAllByTenant(TenantContext.getTenantId(), pageable)
                .map(segmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SegmentDto.Response getById(String id) {
        String tenantId = TenantContext.getTenantId();
        Segment segment = segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        return segmentMapper.toResponse(segment);
    }

    @Transactional
    public SegmentDto.Response create(SegmentDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (segmentRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("Segment", "name", request.getName());
        }

        Segment entity = segmentMapper.toEntity(request);
        entity.setTenantId(tenantId);
        if (request.getSegmentType() != null) {
            entity.setSegmentType(Segment.SegmentType.valueOf(request.getSegmentType().toUpperCase()));
        }

        Segment saved = segmentRepository.save(entity);
        log.info("Segment created: name={}, id={}", saved.getName(), saved.getId());
        eventPublisher.publishCreated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public SegmentDto.Response update(String id, SegmentDto.UpdateRequest request) {
        String tenantId = TenantContext.getTenantId();
        Segment existing = segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getRules() != null) existing.setRules(request.getRules());
        if (request.getScheduleEnabled() != null) existing.setScheduleEnabled(request.getScheduleEnabled());
        if (request.getStatus() != null) existing.setStatus(Segment.SegmentStatus.valueOf(request.getStatus().toUpperCase()));

        Segment saved = segmentRepository.save(existing);
        eventPublisher.publishUpdated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = TenantContext.getTenantId();
        Segment existing = segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        existing.softDelete();
        segmentRepository.save(existing);
        log.info("Segment deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return segmentRepository.countByTenant(TenantContext.getTenantId());
    }
}
