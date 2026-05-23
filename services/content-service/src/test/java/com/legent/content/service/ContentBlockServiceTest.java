package com.legent.content.service;

import com.legent.content.domain.ContentBlock;
import com.legent.content.domain.ContentBlockVersion;
import com.legent.content.repository.ContentBlockRepository;
import com.legent.content.repository.ContentBlockVersionRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentBlockServiceTest {

    @Mock
    private ContentBlockRepository blockRepository;

    @Mock
    private ContentBlockVersionRepository blockVersionRepository;

    private ContentBlockService service;

    @BeforeEach
    void setUp() {
        service = new ContentBlockService(blockRepository, blockVersionRepository);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listVersions_usesDefaultFirstPageRequestWithinTenantWorkspace() {
        ContentBlock block = block();
        ContentBlockVersion version = version(block, 4, false);
        when(blockRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("block-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(block));
        when(blockVersionRepository.findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("block-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                any(Pageable.class)))
                .thenReturn(List.of(version));

        List<ContentBlockVersion> versions = service.listVersions("tenant-1", "workspace-1", "block-1");

        assertThat(versions).containsExactly(version);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(blockVersionRepository).findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("block-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void listVersions_clampsOversizedLimitToMaxFirstPageRequest() {
        ContentBlock block = block();
        when(blockRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("block-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(block));
        when(blockVersionRepository.findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("block-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.listVersions("tenant-1", "workspace-1", "block-1", 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(blockVersionRepository).findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("block-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void publishVersion_updatesTargetAndClearsOtherPublishedVersionsWithoutFullHistoryLoad() {
        ContentBlock block = block();
        ContentBlockVersion version = version(block, 3, false);
        when(blockRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("block-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(block));
        when(blockVersionRepository.findByBlock_IdAndVersionNumberAndTenantIdAndWorkspaceId("block-1", 3, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(version));
        when(blockVersionRepository.save(any(ContentBlockVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContentBlockVersion published = service.publishVersion("tenant-1", "workspace-1", "block-1", 3);

        assertThat(published).isSameAs(version);
        assertThat(version.getIsPublished()).isTrue();
        assertThat(block.getContent()).isEqualTo("content v3");
        assertThat(block.getStyles()).isEqualTo("{\"v\":3}");
        assertThat(block.getSettings()).isEqualTo("{\"settings\":3}");
        verify(blockVersionRepository).save(version);
        verify(blockVersionRepository).clearPublishedVersionsExcept("block-1", "tenant-1", "workspace-1", 3);
        verify(blockVersionRepository, never()).findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                "block-1", "tenant-1", "workspace-1");
    }

    private ContentBlock block() {
        ContentBlock block = new ContentBlock();
        block.setId("block-1");
        block.setTenantId("tenant-1");
        block.setWorkspaceId("workspace-1");
        block.setName("Hero");
        block.setBlockType("HTML");
        block.setContent("content");
        block.setStyles("{}");
        block.setSettings("{}");
        block.setIsGlobal(false);
        return block;
    }

    private ContentBlockVersion version(ContentBlock block, int versionNumber, boolean published) {
        ContentBlockVersion version = new ContentBlockVersion();
        version.setTenantId(block.getTenantId());
        version.setWorkspaceId(block.getWorkspaceId());
        version.setBlock(block);
        version.setVersionNumber(versionNumber);
        version.setContent("content v" + versionNumber);
        version.setStyles("{\"v\":" + versionNumber + "}");
        version.setSettings("{\"settings\":" + versionNumber + "}");
        version.setIsPublished(published);
        return version;
    }
}
