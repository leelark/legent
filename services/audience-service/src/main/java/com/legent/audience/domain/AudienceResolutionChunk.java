package com.legent.audience.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audience_resolution_chunks")
@Getter
@Setter
@NoArgsConstructor
public class AudienceResolutionChunk extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "total_resolved_subscribers", nullable = false)
    private int totalResolvedSubscribers;

    @Column(name = "last_chunk", nullable = false)
    private boolean lastChunk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subscriber_payload", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, String>> subscriberPayload = List.of();
}
