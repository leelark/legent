package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.SendJobCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for send job checkpoint operations.
 */
@Repository
public interface SendJobCheckpointRepository extends JpaRepository<SendJobCheckpoint, String> {

    List<SendJobCheckpoint> findByJobIdOrderBySequenceNumberDesc(String jobId);

    @Query("SELECT c FROM SendJobCheckpoint c WHERE c.jobId = :jobId ORDER BY c.sequenceNumber DESC LIMIT 1")
    Optional<SendJobCheckpoint> findLatestCheckpoint(@Param("jobId") String jobId);

    List<SendJobCheckpoint> findByJobIdAndCheckpointTypeOrderBySequenceNumberDesc(
            String jobId, SendJobCheckpoint.CheckpointType checkpointType);

    @Query("SELECT COUNT(c) FROM SendJobCheckpoint c WHERE c.jobId = :jobId")
    long countByJobId(@Param("jobId") String jobId);
}
