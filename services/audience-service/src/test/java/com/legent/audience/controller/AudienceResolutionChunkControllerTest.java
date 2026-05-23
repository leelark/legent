package com.legent.audience.controller;

import com.legent.audience.service.AudienceResolutionChunkService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AudienceResolutionChunkControllerTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    @Mock
    private AudienceResolutionChunkService service;

    private AutoCloseable mocks;
    private AudienceResolutionChunkController controller;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        controller = new AudienceResolutionChunkController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void getInternalChunkRejectsInvalidTokenBeforeServiceLookup() {
        assertThatThrownBy(() -> controller.getInternalChunk(
                "bad-token",
                "tenant-1",
                "workspace-1",
                "chunk-1",
                "job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));

        verify(service, never()).getChunk("tenant-1", "workspace-1", "job-1", "chunk-1");
    }

    @Test
    void getInternalChunkDelegatesWithTenantWorkspaceScopeWhenTokenMatches() {
        var response = new AudienceResolutionChunkService.ChunkResponse(
                "tenant-1",
                "workspace-1",
                "campaign-1",
                "job-1",
                "chunk-1",
                0,
                1,
                1,
                1,
                true,
                List.of(Map.of("email", "one@example.com")));
        org.mockito.Mockito.when(service.getChunk("tenant-1", "workspace-1", "job-1", "chunk-1"))
                .thenReturn(response);

        var result = controller.getInternalChunk(
                "  " + INTERNAL_TOKEN + "  ",
                "tenant-1",
                "workspace-1",
                "chunk-1",
                "job-1");

        assertThat(result.getData()).isSameAs(response);
        verify(service).getChunk("tenant-1", "workspace-1", "job-1", "chunk-1");
    }
}
