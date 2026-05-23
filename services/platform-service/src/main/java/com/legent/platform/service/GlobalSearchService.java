package com.legent.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.repository.SearchIndexDocRepository;
import com.legent.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    static final int DEFAULT_SEARCH_LIMIT = 50;
    static final int MAX_SEARCH_LIMIT = 200;

    private final SearchIndexDocRepository searchRepository;
    private final ObjectMapper objectMapper;

    public void indexDocument(String tenantId, String entityType, String entityId, String title, String searchableText, Map<String, Object> metadata) {
        String workspaceId = TenantContext.getWorkspaceId();
        String idDoc = documentId(tenantId, workspaceId, entityType, entityId);

        SearchIndexDoc doc = searchRepository.findById(idDoc).orElse(new SearchIndexDoc());
        doc.setId(idDoc);
        doc.setTenantId(tenantId);
        // Tenant-global platform events can intentionally omit workspace context; workspace search APIs below do not expose them.
        doc.setWorkspaceId(workspaceId);
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

    public List<SearchIndexDoc> search(String tenantId, String workspaceId, String query) {
        return search(tenantId, workspaceId, query, DEFAULT_SEARCH_LIMIT);
    }

    List<SearchIndexDoc> search(String tenantId, String workspaceId, String query, Integer limit) {
        // Simplified ILIKE search mapping over all entities.
        // In a true OpenSearch implementation, this constructs a boolean query targeting ngram analyzers.
        return searchRepository.findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
                tenantId, workspaceId, query, PageRequest.of(0, boundedSearchLimit(limit)))
                .getContent();
    }

    private int boundedSearchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    private String documentId(String tenantId, String workspaceId, String entityType, String entityId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return tenantId + ":" + entityType + ":" + entityId;
        }
        return tenantId + ":" + workspaceId + ":" + entityType + ":" + entityId;
    }
}
