package com.legent.audience.service;

import java.util.List;

import java.time.Instant;

import com.legent.audience.domain.ListMembership;
import com.legent.audience.domain.SubscriberList;
import com.legent.audience.dto.SubscriberListDto;
import com.legent.audience.mapper.SubscriberListMapper;
import com.legent.audience.repository.ListMembershipRepository;
import com.legent.audience.repository.SubscriberListRepository;
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
public class SubscriberListService {

    private final SubscriberListRepository listRepository;
    private final ListMembershipRepository membershipRepository;
    private final SubscriberListMapper listMapper;

    @Transactional(readOnly = true)
    public Page<SubscriberListDto.Response> list(Pageable pageable) {
        return listRepository.findAllByTenant(TenantContext.getTenantId(), pageable)
                .map(listMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SubscriberListDto.Response getById(String id) {
        String tenantId = TenantContext.getTenantId();
        SubscriberList entity = listRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));
        return listMapper.toResponse(entity);
    }

    @Transactional
    public SubscriberListDto.Response create(SubscriberListDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (listRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("SubscriberList", "name", request.getName());
        }

        SubscriberList entity = listMapper.toEntity(request);
        entity.setTenantId(tenantId);
        if (request.getListType() != null) {
            entity.setListType(SubscriberList.ListType.valueOf(request.getListType().toUpperCase()));
        }

        SubscriberList saved = listRepository.save(entity);
        log.info("List created: name={}, id={}", saved.getName(), saved.getId());
        return listMapper.toResponse(saved);
    }

    @Transactional
    public SubscriberListDto.Response update(String id, SubscriberListDto.UpdateRequest request) {
        String tenantId = TenantContext.getTenantId();
        SubscriberList existing = listRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getStatus() != null) existing.setStatus(SubscriberList.ListStatus.valueOf(request.getStatus().toUpperCase()));

        SubscriberList saved = listRepository.save(existing);
        return listMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = TenantContext.getTenantId();
        SubscriberList existing = listRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));
        existing.softDelete();
        listRepository.save(existing);
        log.info("List deleted: id={}", id);
    }

    @Transactional
    public void addMembers(String listId, List<String> subscriberIds) {
        String tenantId = TenantContext.getTenantId();
        SubscriberList list = listRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, listId)
                .orElseThrow(() -> new NotFoundException("SubscriberList", listId));

        int added = 0;
        for (String subId : subscriberIds) {
            if (!membershipRepository.existsByListIdAndSubscriberIdAndStatus(listId, subId, ListMembership.MembershipStatus.ACTIVE)) {
                ListMembership m = new ListMembership();
                m.setTenantId(tenantId);
                m.setListId(listId);
                m.setSubscriberId(subId);
                m.setStatus(ListMembership.MembershipStatus.ACTIVE);
                m.setAddedAt(Instant.now());
                membershipRepository.save(m);
                added++;
            }
        }

        list.setMemberCount(membershipRepository.countActiveByList(listId));
        listRepository.save(list);
        log.info("Added {} members to list {}", added, listId);
    }

    @Transactional
    public void removeMembers(String listId, List<String> subscriberIds) {
        String tenantId = TenantContext.getTenantId();
        SubscriberList list = listRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, listId)
                .orElseThrow(() -> new NotFoundException("SubscriberList", listId));

        membershipRepository.removeMembers(listId, subscriberIds);
        list.setMemberCount(membershipRepository.countActiveByList(listId));
        listRepository.save(list);
        log.info("Removed {} members from list {}", subscriberIds.size(), listId);
    }

    @Transactional(readOnly = true)
    public long count() {
        return listRepository.countByTenant(TenantContext.getTenantId());
    }
}
