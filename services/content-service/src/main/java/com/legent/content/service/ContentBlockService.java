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
        String workspaceId = TenantContext.requireWorkspaceId();

        if (blockRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("Block with name '" + request.getName() + "' already exists");
        }

        ContentBlock block = new ContentBlock();
        block.setTenantId(tenantId);
        block.setWorkspaceId(workspaceId);
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
        return getBlock(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @NonNull
    public ContentBlock getBlock(@NonNull String tenantId, @NonNull String workspaceId, @NonNull String id) {
        return java.util.Objects.requireNonNull(blockRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(id, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Content block not found")));
    }

    @Transactional
    public ContentBlock updateBlock(@NonNull String tenantId, @NonNull String id, ContentBlockDto.Create request) {
        return updateBlock(tenantId, TenantContext.requireWorkspaceId(), id, request);
    }

    @Transactional
    public ContentBlock updateBlock(@NonNull String tenantId, @NonNull String workspaceId, @NonNull String id, ContentBlockDto.Create request) {
        ContentBlock block = getBlock(tenantId, workspaceId, id);

        if (request.getName() != null && !request.getName().equals(block.getName())) {
            if (blockRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
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
        deleteBlock(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public void deleteBlock(@NonNull String tenantId, @NonNull String workspaceId, @NonNull String id) {
        ContentBlock block = getBlock(tenantId, workspaceId, id);
        block.setDeletedAt(Instant.now());
        blockRepository.save(block);
    }

    public Page<ContentBlock> listBlocks(String tenantId, Pageable pageable) {
        return listBlocks(tenantId, TenantContext.requireWorkspaceId(), pageable);
    }

    public Page<ContentBlock> listBlocks(String tenantId, String workspaceId, Pageable pageable) {
        return blockRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(tenantId, workspaceId, pageable);
    }

    public List<ContentBlock> listGlobalBlocks(String tenantId) {
        return listGlobalBlocks(tenantId, TenantContext.requireWorkspaceId());
    }

    public List<ContentBlock> listGlobalBlocks(String tenantId, String workspaceId) {
        return blockRepository.findByTenantIdAndWorkspaceIdAndIsGlobalIsTrueAndDeletedAtIsNull(tenantId, workspaceId);
    }

    @Transactional
    public ContentBlockVersion createVersion(String tenantId, String blockId, EmailStudioDto.ContentBlockVersionRequest request) {
        return createVersion(tenantId, TenantContext.requireWorkspaceId(), blockId, request);
    }

    @Transactional
    public ContentBlockVersion createVersion(String tenantId, String workspaceId, String blockId, EmailStudioDto.ContentBlockVersionRequest request) {
        ContentBlock block = getBlock(tenantId, workspaceId, blockId);
        ContentBlockVersion version = new ContentBlockVersion();
        version.setTenantId(tenantId);
        version.setWorkspaceId(workspaceId);
        version.setBlock(block);
        version.setVersionNumber(nextVersionNumber(blockId, tenantId, workspaceId));
        version.setContent(request.getContent());
        version.setStyles(request.getStyles() != null ? request.getStyles() : block.getStyles());
        version.setSettings(request.getSettings() != null ? request.getSettings() : block.getSettings());
        version.setChanges(request.getChanges());
        version.setIsPublished(false);
        version = blockVersionRepository.save(version);
        if (Boolean.TRUE.equals(request.getPublish())) {
            return publishVersion(tenantId, workspaceId, blockId, version.getVersionNumber());
        }
        return version;
    }

    @Transactional(readOnly = true)
    public List<ContentBlockVersion> listVersions(String tenantId, String blockId) {
        return listVersions(tenantId, TenantContext.requireWorkspaceId(), blockId);
    }

    @Transactional(readOnly = true)
    public List<ContentBlockVersion> listVersions(String tenantId, String workspaceId, String blockId) {
        getBlock(tenantId, workspaceId, blockId);
        return blockVersionRepository.findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(blockId, tenantId, workspaceId);
    }

    @Transactional
    public ContentBlockVersion publishVersion(String tenantId, String blockId, Integer versionNumber) {
        return publishVersion(tenantId, TenantContext.requireWorkspaceId(), blockId, versionNumber);
    }

    @Transactional
    public ContentBlockVersion publishVersion(String tenantId, String workspaceId, String blockId, Integer versionNumber) {
        ContentBlock block = getBlock(tenantId, workspaceId, blockId);
        ContentBlockVersion version = blockVersionRepository.findByBlock_IdAndVersionNumberAndTenantIdAndWorkspaceId(blockId, versionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("ContentBlockVersion", blockId + " v" + versionNumber));
        for (ContentBlockVersion current : blockVersionRepository.findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(blockId, tenantId, workspaceId)) {
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
        return rollbackVersion(tenantId, TenantContext.requireWorkspaceId(), blockId, versionNumber);
    }

    @Transactional
    public ContentBlockVersion rollbackVersion(String tenantId, String workspaceId, String blockId, Integer versionNumber) {
        ContentBlock block = getBlock(tenantId, workspaceId, blockId);
        ContentBlockVersion source = blockVersionRepository.findByBlock_IdAndVersionNumberAndTenantIdAndWorkspaceId(blockId, versionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("ContentBlockVersion", blockId + " v" + versionNumber));
        ContentBlockVersion rollback = new ContentBlockVersion();
        rollback.setTenantId(tenantId);
        rollback.setWorkspaceId(workspaceId);
        rollback.setBlock(block);
        rollback.setVersionNumber(nextVersionNumber(blockId, tenantId, workspaceId));
        rollback.setContent(source.getContent());
        rollback.setStyles(source.getStyles());
        rollback.setSettings(source.getSettings());
        rollback.setChanges("Rollback from block version " + versionNumber);
        rollback.setIsPublished(false);
        rollback = blockVersionRepository.save(rollback);
        return publishVersion(tenantId, workspaceId, blockId, rollback.getVersionNumber());
    }

    private void createInitialVersion(ContentBlock block) {
        ContentBlockVersion version = new ContentBlockVersion();
        version.setTenantId(block.getTenantId());
        version.setWorkspaceId(block.getWorkspaceId());
        version.setBlock(block);
        version.setVersionNumber(1);
        version.setContent(block.getContent());
        version.setStyles(block.getStyles());
        version.setSettings(block.getSettings());
        version.setChanges("Initial reusable content block snapshot");
        version.setIsPublished(true);
        blockVersionRepository.save(version);
    }

    private int nextVersionNumber(String blockId, String tenantId, String workspaceId) {
        return blockVersionRepository.findFirstByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(blockId, tenantId, workspaceId)
                .map(ContentBlockVersion::getVersionNumber)
                .orElse(0) + 1;
    }
}
