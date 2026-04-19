package com.legent.audience.repository;

import com.legent.audience.domain.DataExtensionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExtensionRecordRepository extends JpaRepository<DataExtensionRecord, String> {

    Page<DataExtensionRecord> findByDataExtensionId(String dataExtensionId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM DataExtensionRecord r WHERE r.dataExtensionId = :deId")
    long countByDataExtension(@Param("deId") String dataExtensionId);

    void deleteByDataExtensionId(String dataExtensionId);
}
