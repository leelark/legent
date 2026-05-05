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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor

public class SubscriberListService {

    private final SubscriberListRepository listRepository;
    private final ListMembershipRepository membershipRepository;
    private final SubscriberListMapper listMapper;

    @Transactional(readOnly = true)
    public Page<SubscriberListDto.Response> list(Pageable pageable) {
        return listRepository.findAllByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId(), pageable)
                .map(listMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SubscriberListDto.Response getById(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        SubscriberList entity = listRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));
        return listMapper.toResponse(entity);
    }

    @Transactional
    public SubscriberListDto.Response create(SubscriberListDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        if (listRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("SubscriberList", "name", request.getName());
        }

        SubscriberList entity = listMapper.toEntity(request);
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        if (request.getListType() != null) {
            entity.setListType(SubscriberList.ListType.valueOf(request.getListType().toUpperCase()));
        }

        SubscriberList saved = listRepository.save(entity);
        log.info("List created: name={}, id={}", saved.getName(), saved.getId());
        return listMapper.toResponse(saved);
    }

    @Transactional
    public SubscriberListDto.Response update(String id, SubscriberListDto.UpdateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        SubscriberList existing = listRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getStatus() != null) existing.setStatus(SubscriberList.ListStatus.valueOf(request.getStatus().toUpperCase()));

        SubscriberList saved = listRepository.save(existing);
        return listMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        SubscriberList existing = listRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("SubscriberList", id));
        existing.softDelete();
        listRepository.save(existing);
        log.info("List deleted: id={}", id);
    }

    @Transactional
    public void addMembers(String listId, List<String> subscriberIds) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        SubscriberList list = listRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, listId)
                .orElseThrow(() -> new NotFoundException("SubscriberList", listId));

        int added = 0;
        for (String subId : subscriberIds) {
            if (!membershipRepository.existsByTenantIdAndWorkspaceIdAndListIdAndSubscriberIdAndStatus(
                    tenantId, workspaceId, listId, subId, ListMembership.MembershipStatus.ACTIVE)) {
                ListMembership m = new ListMembership();
                m.setTenantId(tenantId);
                m.setWorkspaceId(workspaceId);
                m.setListId(listId);
                m.setSubscriberId(subId);
                m.setStatus(ListMembership.MembershipStatus.ACTIVE);
                m.setAddedAt(Instant.now());
                membershipRepository.save(m);
                added++;
            }
        }

        list.setMemberCount(membershipRepository.countActiveByList(tenantId, workspaceId, listId));
        listRepository.save(list);
        log.info("Added {} members to list {}", added, listId);
    }

    @Transactional
    public void removeMembers(String listId, List<String> subscriberIds) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        SubscriberList list = listRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, listId)
                .orElseThrow(() -> new NotFoundException("SubscriberList", listId));

        membershipRepository.removeMembers(tenantId, workspaceId, listId, subscriberIds);
        list.setMemberCount(membershipRepository.countActiveByList(tenantId, workspaceId, listId));
        listRepository.save(list);
        log.info("Removed {} members from list {}", subscriberIds.size(), listId);
    }

    @Transactional(readOnly = true)
    public long count() {
        return listRepository.countByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId());
    }
}
