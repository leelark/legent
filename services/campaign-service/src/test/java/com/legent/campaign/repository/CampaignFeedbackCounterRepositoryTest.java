package com.legent.campaign.repository;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:campaign-feedback-counter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ContextConfiguration(classes = CampaignFeedbackCounterRepositoryTest.JpaSlice.class)
class CampaignFeedbackCounterRepositoryTest {

    @Autowired private SendJobRepository sendJobRepository;
    @Autowired private SendBatchRepository sendBatchRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS send_jobs (
                    id varchar(26) primary key,
                    campaign_id varchar(255) not null,
                    workspace_id varchar(64) not null,
                    team_id varchar(64),
                    ownership_scope varchar(32) not null,
                    status varchar(255) not null,
                    scheduled_at timestamp with time zone,
                    started_at timestamp with time zone,
                    completed_at timestamp with time zone,
                    paused_at timestamp with time zone,
                    cancelled_at timestamp with time zone,
                    total_target bigint,
                    total_sent bigint,
                    total_failed bigint,
                    total_bounced bigint,
                    total_suppressed bigint,
                    error_message varchar(255),
                    trigger_source varchar(128),
                    trigger_reference varchar(128),
                    idempotency_key varchar(128),
                    last_checkpoint_at timestamp with time zone,
                    checkpoint_interval integer,
                    can_resume boolean not null,
                    resumed_from_job_id varchar(36),
                    tenant_id varchar(26) not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    created_by varchar(26),
                    deleted_at timestamp with time zone,
                    version bigint not null
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS send_batches (
                    id varchar(26) primary key,
                    batch_size integer not null,
                    campaign_id varchar(255),
                    created_at timestamp with time zone not null,
                    created_by varchar(26),
                    deleted_at timestamp with time zone,
                    domain varchar(255),
                    failure_count integer,
                    job_id varchar(255) not null,
                    last_error varchar(255),
                    ownership_scope varchar(32) not null,
                    payload json,
                    processed_count integer,
                    retry_count integer,
                    status varchar(255) not null,
                    success_count integer,
                    team_id varchar(64),
                    tenant_id varchar(26) not null,
                    updated_at timestamp with time zone not null,
                    version bigint not null,
                    workspace_id varchar(64) not null
                )
                """);
        jdbcTemplate.update("DELETE FROM send_batches");
        jdbcTemplate.update("DELETE FROM send_jobs");
    }

    @Test
    void sentFeedbackUpdatesJobAndPendingBatchCountersAtomically() {
        persistJob("job-1");
        persistBatch("batch-1", SendBatch.BatchStatus.PENDING, 2, 0, 0, 0);
        entityManager.flush();
        entityManager.clear();

        int batchRows = sendBatchRepository.applyDeliveryFeedbackCounters(
                "tenant-1",
                "workspace-1",
                "batch-1",
                false,
                null,
                SendBatch.BatchStatus.PENDING,
                SendBatch.BatchStatus.PROCESSING,
                SendBatch.BatchStatus.COMPLETED,
                SendBatch.BatchStatus.FAILED,
                SendBatch.BatchStatus.PARTIAL,
                Instant.parse("2026-05-21T22:00:00Z"));
        int jobRows = sendJobRepository.incrementSentFeedbackCounter(
                "tenant-1",
                "workspace-1",
                "job-1",
                Instant.parse("2026-05-21T22:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRows).isEqualTo(1);
        assertThat(jobRows).isEqualTo(1);
        SendBatch batch = entityManager.find(SendBatch.class, "batch-1");
        assertThat(batch.getProcessedCount()).isEqualTo(1);
        assertThat(batch.getSuccessCount()).isEqualTo(1);
        assertThat(batch.getFailureCount()).isZero();
        assertThat(batch.getStatus()).isEqualTo(SendBatch.BatchStatus.PROCESSING);
        SendJob job = entityManager.find(SendJob.class, "job-1");
        assertThat(job.getTotalSent()).isEqualTo(1L);
        assertThat(job.getTotalFailed()).isZero();
    }

    @Test
    void failedFeedbackCompletesMixedBatchAsPartialAndRecordsReasonAtomically() {
        persistJob("job-2");
        persistBatch("batch-2", SendBatch.BatchStatus.PROCESSING, 2, 1, 1, 0);
        entityManager.flush();
        entityManager.clear();

        int batchRows = sendBatchRepository.applyDeliveryFeedbackCounters(
                "tenant-1",
                "workspace-1",
                "batch-2",
                true,
                "hard bounce",
                SendBatch.BatchStatus.PENDING,
                SendBatch.BatchStatus.PROCESSING,
                SendBatch.BatchStatus.COMPLETED,
                SendBatch.BatchStatus.FAILED,
                SendBatch.BatchStatus.PARTIAL,
                Instant.parse("2026-05-21T22:01:00Z"));
        int jobRows = sendJobRepository.incrementFailedFeedbackCounter(
                "tenant-1",
                "workspace-1",
                "job-2",
                "hard bounce",
                Instant.parse("2026-05-21T22:01:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRows).isEqualTo(1);
        assertThat(jobRows).isEqualTo(1);
        SendBatch batch = entityManager.find(SendBatch.class, "batch-2");
        assertThat(batch.getProcessedCount()).isEqualTo(2);
        assertThat(batch.getSuccessCount()).isEqualTo(1);
        assertThat(batch.getFailureCount()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(SendBatch.BatchStatus.PARTIAL);
        assertThat(batch.getLastError()).isEqualTo("hard bounce");
        SendJob job = entityManager.find(SendJob.class, "job-2");
        assertThat(job.getTotalSent()).isZero();
        assertThat(job.getTotalFailed()).isEqualTo(1L);
        assertThat(job.getErrorMessage()).isEqualTo("hard bounce");
    }

    private void persistJob(String jobId) {
        SendJob job = new SendJob();
        job.setId(jobId);
        job.setTenantId("tenant-1");
        job.setWorkspaceId("workspace-1");
        job.setCampaignId("campaign-1");
        job.setStatus(SendJob.JobStatus.SENDING);
        entityManager.persist(job);
    }

    private void persistBatch(String batchId,
                              SendBatch.BatchStatus status,
                              int batchSize,
                              int processedCount,
                              int successCount,
                              int failureCount) {
        SendBatch batch = new SendBatch();
        batch.setId(batchId);
        batch.setTenantId("tenant-1");
        batch.setWorkspaceId("workspace-1");
        batch.setJobId(batchId.equals("batch-1") ? "job-1" : "job-2");
        batch.setCampaignId("campaign-1");
        batch.setStatus(status);
        batch.setBatchSize(batchSize);
        batch.setProcessedCount(processedCount);
        batch.setSuccessCount(successCount);
        batch.setFailureCount(failureCount);
        entityManager.persist(batch);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {SendJob.class, SendBatch.class})
    @EnableJpaRepositories(basePackageClasses = {SendJobRepository.class, SendBatchRepository.class})
    static class JpaSlice {
    }
}
