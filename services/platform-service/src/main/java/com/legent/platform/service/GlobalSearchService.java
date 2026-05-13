package com.legent.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.repository.SearchIndexDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final SearchIndexDocRepository searchRepository;
    private final ObjectMapper objectMapper;

    public void indexDocument(String tenantId, String entityType, String entityId, String title, String searchableText, Map<String, Object> metadata) {
        // Find existing or composite ID logic. For brevity we just use entityId
        String idDoc = tenantId + ":" + entityType + ":" + entityId;

        SearchIndexDoc doc = searchRepository.findById(idDoc).orElse(new SearchIndexDoc());
        doc.setId(idDoc);
        doc.setTenantId(tenantId);
        doc.setEntityType(entityType);
        doc.setEntityId(entityId);
        doc.setTitle(title);
        doc.setSearchableText(searchableText);
        doc.setUpdatedAt(Instant.now());

        try {
            if (metadata != null) {
                doc.setMetadata(objectMapper.writeValueAsString(metadata));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to map metadata for search doc", e);
            throw new IllegalStateException("Failed to serialize search metadata", e);
        }

        searchRepository.save(doc);
    }

    public List<SearchIndexDoc> search(String tenantId, String query) {
        // Simplified ILIKE search mapping over all entities.
        // In a true OpenSearch implementation, this constructs a boolean query targeting ngram analyzers.
        return searchRepository.findByTenantIdAndSearchableTextContainingIgnoreCase(tenantId, query);
    }
}
