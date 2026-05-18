package com.legent.audience.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AudienceEventIdempotencyServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AudienceEventIdempotencyService service;

    @Test
    void registerIfNew_MissingWorkspace_ThrowsBeforeJdbcInsert() {
        assertThatThrownBy(() -> service.registerIfNew(
                "tenant-1",
                " ",
                "tracking.ingested",
                "evt-1",
                "idem-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing workspaceId");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void registerIfNew_MissingIdempotencyKey_ThrowsBeforeJdbcInsert() {
        assertThatThrownBy(() -> service.registerIfNew(
                "tenant-1",
                "workspace-1",
                "tracking.ingested",
                "evt-1",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing idempotencyKey");

        verifyNoInteractions(jdbcTemplate);
    }
}
