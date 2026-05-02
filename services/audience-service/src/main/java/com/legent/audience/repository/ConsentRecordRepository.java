package com.legent.audience.repository;

import java.util.List;
import java.util.Optional;

import com.legent.audience.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for consent record operations.
 */
@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, String> {

    List<ConsentRecord> findByTenantIdAndSubscriberId(String tenantId, String subscriberId);

    Optional<ConsentRecord> findByTenantIdAndSubscriberIdAndConsentType(
            String tenantId, String subscriberId, ConsentRecord.ConsentType consentType);

    @Query("SELECT c FROM ConsentRecord c WHERE c.tenantId = :tenantId AND c.subscriberId = :subscriberId AND c.withdrawnDate IS NULL AND c.consentGiven = true")
    List<ConsentRecord> findActiveConsents(@Param("tenantId") String tenantId, @Param("subscriberId") String subscriberId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ConsentRecord c " +
           "WHERE c.tenantId = :tenantId AND c.subscriberId = :subscriberId AND c.consentType = :consentType " +
           "AND c.consentGiven = true AND c.withdrawnDate IS NULL")
    boolean hasActiveConsent(@Param("tenantId") String tenantId,
                              @Param("subscriberId") String subscriberId,
                              @Param("consentType") ConsentRecord.ConsentType consentType);

    List<ConsentRecord> findByTenantIdAndConsentTypeAndConsentGivenTrueAndWithdrawnDateIsNull(
            String tenantId, ConsentRecord.ConsentType consentType);
}
