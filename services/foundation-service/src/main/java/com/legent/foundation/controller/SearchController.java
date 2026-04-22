package com.legent.foundation.controller;

import com.legent.foundation.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/search")
@ConditionalOnProperty(name = "foundation.search.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchController {
    private final GlobalSearchService searchService;

    @GetMapping
    public SearchResponse<Object> search(@RequestParam String q) throws Exception {
        return searchService.search(q);
    }
}
