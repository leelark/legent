package com.legent.foundation.service;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.mockito.Mockito;

class GlobalSearchServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void search_invokesOpenSearch() throws Exception {
        var client = Mockito.mock(OpenSearchClient.class);
        Mockito.when(client.search(Mockito.any(org.opensearch.client.opensearch.core.SearchRequest.class), Mockito.any(Class.class))).thenReturn(Mockito.mock(SearchResponse.class));
        var svc = new com.legent.foundation.service.GlobalSearchService(client);
        svc.search("test");
        Mockito.verify(client).search(Mockito.any(org.opensearch.client.opensearch.core.SearchRequest.class), Mockito.any(Class.class));
    }
}
