package com.legent.audience.service;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.Optional;

import java.util.List;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.mapper.SubscriberMapper;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.cache.service.CacheService;
import com.legent.cache.service.TenantCacheKeyGenerator;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.audience.event.SubscriberEventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Core subscriber lifecycle operations.
 * Handles CRUD, deduplication, bulk upsert, and search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SubscriberMapper subscriberMapper;
    private final CacheService cacheService;
    private final SubscriberEventPublisher eventPublisher;

    private static final Duration CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_SUBSCRIBER_TTL_SECONDS);

    @Transactional(readOnly = true)
    public SubscriberDto.Response getById(String id) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_SUBSCRIBER_PREFIX, id);

        return cacheService.get(cacheKey, SubscriberDto.Response.class)
                .orElseGet(() -> {
                    Subscriber sub = subscriberRepository.findById(id)
                            .filter(s -> s.getTenantId().equals(tenantId) && !s.isDeleted())
                            .orElseThrow(() -> new NotFoundException("Subscriber", id));
                    SubscriberDto.Response resp = subscriberMapper.toResponse(sub);
                    cacheService.set(cacheKey, resp, CACHE_TTL);
                    return resp;
                });
    }

    @Transactional(readOnly = true)
    public SubscriberDto.Response getBySubscriberKey(String subscriberKey) {
        String tenantId = TenantContext.getTenantId();
        Subscriber sub = subscriberRepository.findByTenantIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, subscriberKey)
                .orElseThrow(() -> new NotFoundException("Subscriber", "subscriberKey=" + subscriberKey));
        return subscriberMapper.toResponse(sub);
    }

    @Transactional(readOnly = true)
    public Page<SubscriberDto.Response> list(Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        return subscriberRepository.findAllByTenant(tenantId, pageable)
                .map(subscriberMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubscriberDto.Response> search(String query, String status, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        if (status != null && !status.isBlank()) {
            return subscriberRepository.findByTenantAndStatus(
                    tenantId, Subscriber.SubscriberStatus.valueOf(status.toUpperCase()), pageable
            ).map(subscriberMapper::toResponse);
        }
        if (query != null && !query.isBlank()) {
            return subscriberRepository.searchByTenant(tenantId, query.trim(), pageable)
                    .map(subscriberMapper::toResponse);
        }
        return list(pageable);
    }

    @Transactional
    public SubscriberDto.Response create(SubscriberDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();

        if (subscriberRepository.existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, request.getSubscriberKey())) {
            throw new ConflictException("Subscriber", "subscriberKey", request.getSubscriberKey());
        }

        Subscriber entity = subscriberMapper.toEntity(request);
        entity.setTenantId(tenantId);
        Subscriber saved = subscriberRepository.save(entity);

        log.info("Subscriber created: key={}, id={}", saved.getSubscriberKey(), saved.getId());
        eventPublisher.publishCreated(saved);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public SubscriberDto.Response update(String id, SubscriberDto.UpdateRequest request) {
        String tenantId = TenantContext.getTenantId();
        Subscriber existing = subscriberRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId) && !s.isDeleted())
                .orElseThrow(() -> new NotFoundException("Subscriber", id));

        subscriberMapper.updateEntity(request, existing);

        if (request.getStatus() != null) {
            existing.setStatus(Subscriber.SubscriberStatus.valueOf(request.getStatus().toUpperCase()));
        }

        Subscriber saved = subscriberRepository.save(existing);
        invalidateCache(id);
        eventPublisher.publishUpdated(saved);
        log.info("Subscriber updated: id={}", id);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = TenantContext.getTenantId();
        Subscriber existing = subscriberRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId) && !s.isDeleted())
                .orElseThrow(() -> new NotFoundException("Subscriber", id));

        existing.softDelete();
        subscriberRepository.save(existing);
        invalidateCache(id);
        eventPublisher.publishDeleted(existing);
        log.info("Subscriber deleted: id={}", id);
    }

    /**
     * Bulk upsert with deduplication.
     * Dedup order: subscriber_key → email → alternate key.
     */
    @Transactional
    public SubscriberDto.BulkUpsertResponse bulkUpsert(SubscriberDto.BulkUpsertRequest request) {
        String tenantId = TenantContext.getTenantId();
        int created = 0, updated = 0, errors = 0;
        List<SubscriberDto.BulkError> errorDetails = new ArrayList<>();

        for (int i = 0; i < request.getSubscribers().size(); i++) {
            SubscriberDto.CreateRequest subReq = request.getSubscribers().get(i);
            try {
                Optional<Subscriber> existing = resolveExisting(tenantId, subReq, request.getDeduplicationKey());

                if (existing.isPresent() && request.isUpdateExisting()) {
                    Subscriber entity = existing.get();
                    applyUpdate(entity, subReq);
                    subscriberRepository.save(entity);
                    updated++;
                } else if (existing.isEmpty()) {
                    Subscriber entity = subscriberMapper.toEntity(subReq);
                    entity.setTenantId(tenantId);
                    subscriberRepository.save(entity);
                    created++;
                }
            } catch (Exception e) {
                errors++;
                errorDetails.add(SubscriberDto.BulkError.builder()
                        .index(i).subscriberKey(subReq.getSubscriberKey()).message(e.getMessage()).build());
            }
        }

        log.info("Bulk upsert: created={}, updated={}, errors={}", created, updated, errors);
        return SubscriberDto.BulkUpsertResponse.builder()
                .created(created).updated(updated).errors(errors).errorDetails(errorDetails).build();
    }

    @Transactional(readOnly = true)
    public long count() {
        return subscriberRepository.countByTenant(TenantContext.getTenantId());
    }

    // ── Helpers ──

    private Optional<Subscriber> resolveExisting(String tenantId, SubscriberDto.CreateRequest req, String deduplicationKey) {
        if ("email".equalsIgnoreCase(deduplicationKey)) {
            return subscriberRepository.findByTenantIdAndEmailAndDeletedAtIsNull(tenantId, req.getEmail());
        }
        // Default: subscriber_key
        return subscriberRepository.findByTenantIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, req.getSubscriberKey());
    }

    private void applyUpdate(Subscriber entity, SubscriberDto.CreateRequest req) {
        if (req.getEmail() != null) entity.setEmail(req.getEmail());
        if (req.getFirstName() != null) entity.setFirstName(req.getFirstName());
        if (req.getLastName() != null) entity.setLastName(req.getLastName());
        if (req.getPhone() != null) entity.setPhone(req.getPhone());
        if (req.getCustomFields() != null) entity.setCustomFields(req.getCustomFields());
    }

    private void invalidateCache(String subscriberId) {
        String key = TenantCacheKeyGenerator.key(AppConstants.CACHE_SUBSCRIBER_PREFIX, subscriberId);
        cacheService.delete(key);
    }
}
