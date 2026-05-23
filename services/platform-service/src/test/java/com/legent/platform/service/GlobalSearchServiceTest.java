package com.legent.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.repository.SearchIndexDocRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock private SearchIndexDocRepository searchRepository;
    @Mock private ObjectMapper objectMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void indexDocument_whenMetadataSerializationFails_doesNotSavePartialDocument() throws JsonProcessingException {
        TenantContext.setWorkspaceId("w1");
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findById("t1:w1:CAMPAIGN:c1")).thenReturn(Optional.of(new SearchIndexDoc()));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("cannot serialize metadata") {});

        assertThrows(IllegalStateException.class, () -> service.indexDocument(
                "t1", "CAMPAIGN", "c1", "Campaign", "copy", Map.of("key", new Object())));

        verify(searchRepository, never()).save(any());
    }

    @Test
    void indexDocumentUsesWorkspaceContextInDocumentIdAndField() {
        TenantContext.setWorkspaceId("w1");
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findById("t1:w1:CAMPAIGN:c1")).thenReturn(Optional.empty());

        service.indexDocument("t1", "CAMPAIGN", "c1", "Campaign", "copy", null);

        ArgumentCaptor<SearchIndexDoc> captor = ArgumentCaptor.forClass(SearchIndexDoc.class);
        verify(searchRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("t1:w1:CAMPAIGN:c1");
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo("w1");
    }

    @Test
    void indexDocumentWithoutWorkspaceContextPreservesTenantGlobalDocumentId() {
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findById("t1:CAMPAIGN:c1")).thenReturn(Optional.empty());

        service.indexDocument("t1", "CAMPAIGN", "c1", "Campaign", "copy", null);

        ArgumentCaptor<SearchIndexDoc> captor = ArgumentCaptor.forClass(SearchIndexDoc.class);
        verify(searchRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("t1:CAMPAIGN:c1");
        assertThat(captor.getValue().getWorkspaceId()).isNull();
    }

    @Test
    void searchFiltersByTenantAndWorkspace() {
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                eq("t1"), eq("w1"), eq("campaign"), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, GlobalSearchService.DEFAULT_SEARCH_LIMIT), false));

        service.search("t1", "w1", "campaign");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchRepository).findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                eq("t1"), eq("w1"), eq("campaign"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(GlobalSearchService.DEFAULT_SEARCH_LIMIT);
    }

    @Test
    void searchReturnsExistingListShapeFromBoundedSlice() {
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        SearchIndexDoc doc = new SearchIndexDoc();
        doc.setId("t1:w1:CAMPAIGN:c1");
        when(searchRepository.findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                eq("t1"), eq("w1"), eq("campaign"), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(doc), PageRequest.of(0, GlobalSearchService.DEFAULT_SEARCH_LIMIT), false));

        List<SearchIndexDoc> results = service.search("t1", "w1", "campaign");

        assertThat(results).containsExactly(doc);
    }

    @Test
    void searchClampsOversizedLimitToMaxFirstPageRequest() {
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                eq("t1"), eq("w1"), eq("campaign"), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, GlobalSearchService.MAX_SEARCH_LIMIT), false));

        service.search("t1", "w1", "campaign", GlobalSearchService.MAX_SEARCH_LIMIT + 1_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchRepository).findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                eq("t1"), eq("w1"), eq("campaign"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(GlobalSearchService.MAX_SEARCH_LIMIT);
    }
}
