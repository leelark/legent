package com.legent.audience.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.legent.audience.domain.ConsentRecord;
import com.legent.audience.domain.DoubleOptInToken;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.event.AudienceEventPublisher;
import com.legent.audience.repository.ConsentRecordRepository;
import com.legent.audience.repository.DoubleOptInTokenRepository;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing subscriber consent and double opt-in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordRepository consentRepository;
    private final DoubleOptInTokenRepository tokenRepository;
    private final SubscriberRepository subscriberRepository;
    private final AudienceEventPublisher eventPublisher;

    @Value("${legent.consent.double-opt-in.expiry-hours:48}")
    private int doubleOptInExpiryHours;

    /**
     * Record or update consent for a subscriber.
     */
    @Transactional
    public ConsentRecord recordConsent(String tenantId, String subscriberId,
                                        ConsentRecord.ConsentType consentType,
                                        boolean consentGiven,
                                        ConsentRecord.ConsentSource source,
                                        String ipAddress,
                                        String userAgent) {
        Optional<ConsentRecord> existingOpt = consentRepository
                .findByTenantIdAndSubscriberIdAndConsentType(tenantId, subscriberId, consentType);

        ConsentRecord record;
        if (existingOpt.isPresent()) {
            record = existingOpt.get();
            record.setConsentGiven(consentGiven);
            if (!consentGiven) {
                record.setWithdrawnDate(Instant.now());
            } else {
                record.setWithdrawnDate(null);
                record.setConsentDate(Instant.now());
            }
        } else {
            record = new ConsentRecord();
            record.setTenantId(tenantId);
            record.setSubscriberId(subscriberId);
            record.setConsentType(consentType);
            record.setConsentGiven(consentGiven);
            record.setConsentSource(source);
            record.setConsentDate(Instant.now());
        }

        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);

        ConsentRecord saved = consentRepository.save(record);
        log.info("Consent recorded: tenant={}, subscriber={}, type={}, given={}",
                tenantId, subscriberId, consentType, consentGiven);

        // Publish consent event
        eventPublisher.publishConsentUpdated(tenantId, subscriberId, consentType.name(), consentGiven);

        return saved;
    }

    /**
     * Withdraw consent for a subscriber.
     */
    @Transactional
    public ConsentRecord withdrawConsent(String tenantId, String subscriberId,
                                          ConsentRecord.ConsentType consentType,
                                          String reason) {
        ConsentRecord record = consentRepository
                .findByTenantIdAndSubscriberIdAndConsentType(tenantId, subscriberId, consentType)
                .orElseThrow(() -> new NotFoundException("ConsentRecord", subscriberId + "/" + consentType));

        record.setConsentGiven(false);
        record.setWithdrawnDate(Instant.now());
        record.setNotes(reason);

        ConsentRecord saved = consentRepository.save(record);
        log.info("Consent withdrawn: tenant={}, subscriber={}, type={}",
                tenantId, subscriberId, consentType);

        eventPublisher.publishConsentWithdrawn(tenantId, subscriberId, consentType.name());

        return saved;
    }

    /**
     * Get all consent records for a subscriber.
     */
    @Transactional(readOnly = true)
    public List<ConsentRecord> getSubscriberConsents(String tenantId, String subscriberId) {
        return consentRepository.findByTenantIdAndSubscriberId(tenantId, subscriberId);
    }

    /**
     * Check if subscriber has active consent for a specific type.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(String tenantId, String subscriberId,
                                      ConsentRecord.ConsentType consentType) {
        return consentRepository.hasActiveConsent(tenantId, subscriberId, consentType);
    }

    /**
     * Check if subscriber can receive marketing emails.
     */
    @Transactional(readOnly = true)
    public boolean canSendMarketingEmail(String tenantId, String subscriberId) {
        // Check subscriber status
        Optional<Subscriber> subscriberOpt = subscriberRepository.findByTenantIdAndId(tenantId, subscriberId);
        if (subscriberOpt.isEmpty()) {
            return false;
        }

        Subscriber subscriber = subscriberOpt.get();
        if (subscriber.getStatus() != Subscriber.SubscriberStatus.ACTIVE) {
            return false;
        }

        // Check for double opt-in if required
        if (!subscriber.isDoubleOptInConfirmed()) {
            return false;
        }

        // Check consent
        return hasActiveConsent(tenantId, subscriberId, ConsentRecord.ConsentType.EMAIL_MARKETING);
    }

    /**
     * Create a double opt-in token for a subscriber.
     */
    @Transactional
    public DoubleOptInToken createDoubleOptInToken(String tenantId, String subscriberId,
                                                    String email,
                                                    String ipAddress,
                                                    String userAgent) {
        // Check if there's already a pending token
        Optional<DoubleOptInToken> existingOpt = tokenRepository.findPendingTokenForSubscriber(tenantId, subscriberId);
        if (existingOpt.isPresent()) {
            DoubleOptInToken existing = existingOpt.get();
            if (!existing.isExpired()) {
                log.info("Reusing existing double opt-in token for subscriber {}", subscriberId);
                return existing;
            }
            // Mark old token as expired
            existing.setStatus(DoubleOptInToken.TokenStatus.EXPIRED);
            tokenRepository.save(existing);
        }

        // Generate secure token
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        DoubleOptInToken token = new DoubleOptInToken();
        token.setTenantId(tenantId);
        token.setSubscriberId(subscriberId);
        token.setTokenHash(tokenHash);
        token.setEmail(email);
        token.setStatus(DoubleOptInToken.TokenStatus.PENDING);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(doubleOptInExpiryHours)));
        token.setIpAddress(ipAddress);
        token.setUserAgent(userAgent);

        DoubleOptInToken saved = tokenRepository.save(token);
        log.info("Double opt-in token created: subscriber={}, expires={}", subscriberId, token.getExpiresAt());

        // Store raw token temporarily for sending email (in real implementation,
        // this would be passed to an email service)
        // Note: In production, the raw token should be sent to the user via email
        // and only the hash is stored in the database

        return saved;
    }

    /**
     * Confirm double opt-in using token.
     */
    @Transactional
    public boolean confirmDoubleOptIn(String tenantId, String rawToken,
                                       String ipAddress, String userAgent) {
        String tokenHash = hashToken(rawToken);

        DoubleOptInToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new NotFoundException("DoubleOptInToken", "Invalid token"));

        if (!token.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Token does not match tenant");
        }

        if (token.getStatus() != DoubleOptInToken.TokenStatus.PENDING) {
            log.warn("Double opt-in token already processed: {}", token.getId());
            return false;
        }

        if (token.isExpired()) {
            token.setStatus(DoubleOptInToken.TokenStatus.EXPIRED);
            tokenRepository.save(token);
            throw new IllegalStateException("Double opt-in token has expired");
        }

        // Update token status
        token.setStatus(DoubleOptInToken.TokenStatus.CONFIRMED);
        token.setConfirmedAt(Instant.now());
        tokenRepository.save(token);

        // Update subscriber
        Subscriber subscriber = subscriberRepository
                .findByTenantIdAndId(tenantId, token.getSubscriberId())
                .orElseThrow(() -> new NotFoundException("Subscriber", token.getSubscriberId()));

        subscriber.setDoubleOptInConfirmed(true);
        subscriber.setDoubleOptInConfirmedAt(Instant.now());
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        subscriberRepository.save(subscriber);

        // Record consent
        recordConsent(tenantId, token.getSubscriberId(),
                ConsentRecord.ConsentType.EMAIL_MARKETING, true,
                ConsentRecord.ConsentSource.DOUBLE_OPT_IN, ipAddress, userAgent);

        log.info("Double opt-in confirmed: subscriber={}", token.getSubscriberId());

        eventPublisher.publishDoubleOptInConfirmed(tenantId, token.getSubscriberId(), token.getEmail());

        return true;
    }

    /**
     * Clean up expired double opt-in tokens.
     */
    @Transactional
    public int cleanupExpiredTokens(String tenantId) {
        int count = tokenRepository.markExpiredTokens(tenantId, Instant.now());
        if (count > 0) {
            log.info("Cleaned up {} expired double opt-in tokens for tenant {}", count, tenantId);
        }
        return count;
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
