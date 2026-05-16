package com.legent.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.content.domain.Asset;
import com.legent.content.domain.ContentBlock;
import com.legent.content.domain.ContentBlockVersion;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.AssetDto;
import com.legent.content.dto.ContentBlockDto;
import com.legent.content.dto.TemplateDto;
import com.legent.content.repository.AssetRepository;
import com.legent.content.repository.ContentBlockRepository;
import com.legent.content.repository.ContentBlockVersionRepository;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentWorkspaceScopeServiceTest {

    private static final String TENANT_ID = "tenant_123";
    private static final String WORKSPACE_ID = "workspace_123";

    @Mock
    private EmailTemplateRepository templateRepository;

    @Mock
    private ContentBlockRepository blockRepository;

    @Mock
    private ContentBlockVersionRepository blockVersionRepository;

    @Mock
    private AssetRepository assetRepository;

    @BeforeEach
    void setUpContext() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void createTemplateSetsWorkspaceAndUsesWorkspaceScopedNameCheck() {
        TemplateDto.Create request = new TemplateDto.Create();
        request.setName("Welcome");
        request.setSubject("Hello");
        request.setBody("<p>Hello</p>");

        when(templateRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "Welcome")).thenReturn(false);
        when(templateRepository.save(any(EmailTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateService service = new TemplateService(templateRepository, null, null, null);
        EmailTemplate template = service.createTemplate(request);

        assertEquals(TENANT_ID, template.getTenantId());
        assertEquals(WORKSPACE_ID, template.getWorkspaceId());
        verify(templateRepository).existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "Welcome");
    }

    @Test
    void createBlockSetsWorkspaceOnBlockAndInitialVersion() {
        ContentBlockDto.Create request = new ContentBlockDto.Create();
        request.setName("Hero");
        request.setBlockType("HTML");
        request.setContent("<p>Hero</p>");

        when(blockRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "Hero")).thenReturn(false);
        when(blockRepository.save(any(ContentBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockVersionRepository.save(any(ContentBlockVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContentBlockService service = new ContentBlockService(blockRepository, blockVersionRepository);
        ContentBlock block = service.createBlock(request);

        assertEquals(WORKSPACE_ID, block.getWorkspaceId());
        ArgumentCaptor<ContentBlockVersion> versionCaptor = ArgumentCaptor.forClass(ContentBlockVersion.class);
        verify(blockVersionRepository).save(versionCaptor.capture());
        assertEquals(WORKSPACE_ID, versionCaptor.getValue().getWorkspaceId());
    }

    @Test
    void createAssetSetsWorkspaceAndUsesWorkspaceScopedFileCheck() {
        AssetDto.Create request = AssetDto.Create.builder()
                .name("logo.png")
                .url("https://assets.example/logo.png")
                .contentType("image/png")
                .size(100L)
                .build();

        when(assetRepository.existsByTenantIdAndWorkspaceIdAndFileNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "logo.png")).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetService service = new AssetService(assetRepository, new ObjectMapper());
        Asset asset = service.createAsset(request);

        assertEquals(WORKSPACE_ID, asset.getWorkspaceId());
        verify(assetRepository).existsByTenantIdAndWorkspaceIdAndFileNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "logo.png");
    }
}
