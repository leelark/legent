package com.legent.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.SearchIndexDoc;
import com.legent.platform.repository.SearchIndexDocRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock private SearchIndexDocRepository searchRepository;
    @Mock private ObjectMapper objectMapper;

    @Test
    void indexDocument_whenMetadataSerializationFails_doesNotSavePartialDocument() throws JsonProcessingException {
        GlobalSearchService service = new GlobalSearchService(searchRepository, objectMapper);
        when(searchRepository.findById("t1:CAMPAIGN:c1")).thenReturn(Optional.of(new SearchIndexDoc()));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("cannot serialize metadata") {});

        assertThrows(IllegalStateException.class, () -> service.indexDocument(
                "t1", "CAMPAIGN", "c1", "Campaign", "copy", Map.of("key", new Object())));

        verify(searchRepository, never()).save(any());
    }
}
