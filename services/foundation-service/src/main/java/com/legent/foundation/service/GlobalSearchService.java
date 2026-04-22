package com.legent.foundation.service;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "foundation.search.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GlobalSearchService {
    private final OpenSearchClient openSearchClient;

    public SearchResponse<Object> search(String query) throws Exception {
        SearchRequest req = new SearchRequest.Builder()
            .index("*")
            .query(q -> q.queryString(qs -> qs.query(query)))
            .build();
        return openSearchClient.search(req, Object.class);
    }
}
