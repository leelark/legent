package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.ContentBlock;
import com.legent.content.domain.ContentBlockVersion;
import com.legent.content.dto.ContentBlockDto;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.ContentBlockRepository;
import com.legent.content.repository.ContentBlockVersionRepository;
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
    private final ContentBlockVersionRepository blockVersionRepository;

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
        createInitialVersion(savedBlock);
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

    @Transactional
    public ContentBlockVersion createVersion(String tenantId, String blockId, EmailStudioDto.ContentBlockVersionRequest request) {
        ContentBlock block = getBlock(tenantId, blockId);
        ContentBlockVersion version = new ContentBlockVersion();
        version.setTenantId(tenantId);
        version.setBlock(block);
        version.setVersionNumber(nextVersionNumber(blockId, tenantId));
        version.setContent(request.getContent());
        version.setStyles(request.getStyles() != null ? request.getStyles() : block.getStyles());
        version.setSettings(request.getSettings() != null ? request.getSettings() : block.getSettings());
        version.setChanges(request.getChanges());
        version.setIsPublished(false);
        version = blockVersionRepository.save(version);
        if (Boolean.TRUE.equals(request.getPublish())) {
            return publishVersion(tenantId, blockId, version.getVersionNumber());
        }
        return version;
    }

    @Transactional(readOnly = true)
    public List<ContentBlockVersion> listVersions(String tenantId, String blockId) {
        getBlock(tenantId, blockId);
        return blockVersionRepository.findByBlock_IdAndTenantIdOrderByVersionNumberDesc(blockId, tenantId);
    }

    @Transactional
    public ContentBlockVersion publishVersion(String tenantId, String blockId, Integer versionNumber) {
        ContentBlock block = getBlock(tenantId, blockId);
        ContentBlockVersion version = blockVersionRepository.findByBlock_IdAndVersionNumberAndTenantId(blockId, versionNumber, tenantId)
                .orElseThrow(() -> new NotFoundException("ContentBlockVersion", blockId + " v" + versionNumber));
        for (ContentBlockVersion current : blockVersionRepository.findByBlock_IdAndTenantIdOrderByVersionNumberDesc(blockId, tenantId)) {
            boolean published = Objects.equals(current.getVersionNumber(), versionNumber);
            if (!Objects.equals(current.getIsPublished(), published)) {
                current.setIsPublished(published);
                blockVersionRepository.save(current);
            }
        }
        block.setContent(version.getContent());
        block.setStyles(version.getStyles());
        block.setSettings(version.getSettings());
        blockRepository.save(block);
        return version;
    }

    @Transactional
    public ContentBlockVersion rollbackVersion(String tenantId, String blockId, Integer versionNumber) {
        ContentBlock block = getBlock(tenantId, blockId);
        ContentBlockVersion source = blockVersionRepository.findByBlock_IdAndVersionNumberAndTenantId(blockId, versionNumber, tenantId)
                .orElseThrow(() -> new NotFoundException("ContentBlockVersion", blockId + " v" + versionNumber));
        ContentBlockVersion rollback = new ContentBlockVersion();
        rollback.setTenantId(tenantId);
        rollback.setBlock(block);
        rollback.setVersionNumber(nextVersionNumber(blockId, tenantId));
        rollback.setContent(source.getContent());
        rollback.setStyles(source.getStyles());
        rollback.setSettings(source.getSettings());
        rollback.setChanges("Rollback from block version " + versionNumber);
        rollback.setIsPublished(false);
        rollback = blockVersionRepository.save(rollback);
        return publishVersion(tenantId, blockId, rollback.getVersionNumber());
    }

    private void createInitialVersion(ContentBlock block) {
        ContentBlockVersion version = new ContentBlockVersion();
        version.setTenantId(block.getTenantId());
        version.setBlock(block);
        version.setVersionNumber(1);
        version.setContent(block.getContent());
        version.setStyles(block.getStyles());
        version.setSettings(block.getSettings());
        version.setChanges("Initial reusable content block snapshot");
        version.setIsPublished(true);
        blockVersionRepository.save(version);
    }

    private int nextVersionNumber(String blockId, String tenantId) {
        return blockVersionRepository.findFirstByBlock_IdAndTenantIdOrderByVersionNumberDesc(blockId, tenantId)
                .map(ContentBlockVersion::getVersionNumber)
                .orElse(0) + 1;
    }
}
