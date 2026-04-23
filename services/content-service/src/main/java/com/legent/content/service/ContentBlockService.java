package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.ContentBlock;
import com.legent.content.dto.ContentBlockDto;
import com.legent.content.repository.ContentBlockRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContentBlockService {

    private final ContentBlockRepository blockRepository;

    @Transactional
    public ContentBlock createBlock(ContentBlockDto.Create request) {
        String tenantId = TenantContext.requireTenantId();

        if (blockRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("Block with name '" + request.getName() + "' already exists");
        }

        ContentBlock block = new ContentBlock();
        block.setTenantId(tenantId);
        block.setName(request.getName());
        block.setBlockType(request.getBlockType());
        block.setContent(request.getContent());
        block.setStyles(request.getStyles());
        block.setSettings(request.getSettings());
        block.setIsGlobal(request.getIsGlobal() != null && request.getIsGlobal());

        ContentBlock savedBlock = blockRepository.save(block);
        return Objects.requireNonNull(savedBlock, "Saved block cannot be null");
    }

    @NonNull
    public ContentBlock getBlock(@NonNull String tenantId, @NonNull String id) {
        return java.util.Objects.requireNonNull(blockRepository.findById(id)
                .filter(block -> tenantId.equals(block.getTenantId()) && block.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Content block not found")));
    }

    @Transactional
    public ContentBlock updateBlock(@NonNull String tenantId, @NonNull String id, ContentBlockDto.Create request) {
        ContentBlock block = getBlock(tenantId, id);

        if (request.getName() != null && !request.getName().equals(block.getName())) {
            if (blockRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
                throw new ConflictException("Block with name '" + request.getName() + "' already exists");
            }
            block.setName(request.getName());
        }
        if (request.getBlockType() != null) {
            block.setBlockType(request.getBlockType());
        }
        if (request.getContent() != null) {
            block.setContent(request.getContent());
        }
        if (request.getStyles() != null) {
            block.setStyles(request.getStyles());
        }
        if (request.getSettings() != null) {
            block.setSettings(request.getSettings());
        }
        if (request.getIsGlobal() != null) {
            block.setIsGlobal(request.getIsGlobal());
        }

        blockRepository.save(block);
        return block;
    }

    @Transactional
    public void deleteBlock(@NonNull String tenantId, @NonNull String id) {
        ContentBlock block = getBlock(tenantId, id);
        block.setDeletedAt(Instant.now());
        blockRepository.save(block);
    }

    public Page<ContentBlock> listBlocks(String tenantId, Pageable pageable) {
        return blockRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    public List<ContentBlock> listGlobalBlocks(String tenantId) {
        return blockRepository.findByTenantIdAndIsGlobalIsTrueAndDeletedAtIsNull(tenantId);
    }
}
