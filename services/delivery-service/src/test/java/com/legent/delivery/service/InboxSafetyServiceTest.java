package com.legent.delivery.service;

import com.legent.delivery.domain.InboxSafetyEvaluation;
import com.legent.delivery.repository.InboxSafetyEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxSafetyServiceTest {

    @Mock private InboxSafetyEvaluationRepository inboxSafetyEvaluationRepository;

    private JdbcTemplate jdbcTemplate;
    private InboxSafetyService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource());
        jdbcTemplate.execute("""
                CREATE TABLE suppression_signals (
                    tenant_id VARCHAR(64) NOT NULL,
                    workspace_id VARCHAR(64) NOT NULL,
                    email VARCHAR(320) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    deleted_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE message_logs (
                    tenant_id VARCHAR(64) NOT NULL,
                    workspace_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    status VARCHAR(32),
                    failure_class VARCHAR(64)
                )
                """);
        service = new InboxSafetyService(inboxSafetyEvaluationRepository, jdbcTemplate);
        when(inboxSafetyEvaluationRepository.save(any(InboxSafetyEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void evaluate_allowsSameEmailSuppressedInDifferentWorkspace() {
        insertSuppression("tenant-1", "workspace-a", "user@example.com", "HARD_BOUNCE", false);

        InboxSafetyService.InboxSafetyResult result = service.evaluate(request("tenant-1", "workspace-b", "user@example.com"));

        assertThat(result.decision()).isEqualTo(InboxSafetyService.SafetyDecision.ALLOW);
        assertThat(result.reasonCodes()).doesNotContain("RECIPIENT_SUPPRESSED");
    }

    @Test
    void evaluate_allowsDeletedSuppression() {
        insertSuppression("tenant-1", "workspace-a", "user@example.com", "COMPLAINT", true);

        InboxSafetyService.InboxSafetyResult result = service.evaluate(request("tenant-1", "workspace-a", "user@example.com"));

        assertThat(result.decision()).isEqualTo(InboxSafetyService.SafetyDecision.ALLOW);
        assertThat(result.reasonCodes()).doesNotContain("RECIPIENT_SUPPRESSED");
    }

    @Test
    void evaluate_blocksActiveSuppression() {
        insertSuppression("tenant-1", "workspace-a", "user@example.com", "HARD_BOUNCE", false);

        InboxSafetyService.InboxSafetyResult result = service.evaluate(request("tenant-1", "workspace-a", "user@example.com"));

        assertThat(result.decision()).isEqualTo(InboxSafetyService.SafetyDecision.BLOCK);
        assertThat(result.reasonCodes()).contains("RECIPIENT_SUPPRESSED");
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void insertSuppression(String tenantId, String workspaceId, String email, String type, boolean deleted) {
        Timestamp deletedAt = deleted ? Timestamp.from(Instant.now()) : null;
        jdbcTemplate.update("""
                INSERT INTO suppression_signals (tenant_id, workspace_id, email, type, deleted_at)
                VALUES (?, ?, ?, ?, ?)
                """, tenantId, workspaceId, email, type, deletedAt);
    }

    private InboxSafetyService.InboxSafetyRequest request(String tenantId, String workspaceId, String email) {
        return new InboxSafetyService.InboxSafetyRequest(
                tenantId,
                workspaceId,
                null,
                "job-1",
                "batch-1",
                "message-1",
                "subscriber-1",
                email,
                "sender@example.com",
                "example.com",
                "provider-1",
                "Hello",
                "<html><body>Reviewed campaign content.</body></html>",
                null,
                null,
                "tenant-1:workspace-a:example.com",
                "READY");
    }
}
