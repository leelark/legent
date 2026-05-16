package com.legent.deliverability.service;

import com.legent.common.exception.NotFoundException;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.domain.SenderDomainVerificationHistory;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SenderDomainVerificationHistoryRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xbill.DNS.TextParseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainVerificationService {

    private static final String TOKEN_PREFIX = "legent-domain-verification=";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SenderDomainRepository domainRepository;
    private final SenderDomainVerificationHistoryRepository historyRepository;
    private final DnsTxtResolver dnsTxtResolver;

    @Value("${legent.deliverability.mock-dns:false}")
    private boolean mockDns;

    @Value("${legent.deliverability.sender-domain.challenge-ttl-hours:168}")
    private long challengeTtlHours;

    @Value("${legent.deliverability.sender-domain.proof-max-age-hours:720}")
    private long proofMaxAgeHours;

    public SenderDomain issueChallenge(SenderDomain domain) {
        Instant now = Instant.now();
        String token = newToken();
        domain.setVerificationTokenHash(hashToken(token));
        domain.setVerificationRecordName(challengeRecordName(domain.getDomainName()));
        domain.setVerificationRecordValue(TOKEN_PREFIX + token);
        domain.setVerificationTokenIssuedAt(now);
        domain.setVerificationTokenExpiresAt(now.plus(Math.max(1, challengeTtlHours), ChronoUnit.HOURS));
        domain.setOwnershipTokenVerified(false);
        domain.setOwnershipTokenVerifiedAt(null);
        domain.setStatus(SenderDomain.VerificationStatus.PENDING);
        domain.setIsActive(false);
        domain.setSpfVerified(false);
        domain.setDkimVerified(false);
        domain.setDmarcVerified(false);
        domain.setVerificationFailureReason(null);
        domain.setLastVerifiedAt(null);
        domain.setVerificationToken(token);
        return domain;
    }

    public SenderDomain regenerateChallenge(String domainId) {
        SenderDomain domain = ownedDomain(domainId);
        return domainRepository.save(issueChallenge(domain));
    }

    public SenderDomain verifyDomain(String domainId) {
        SenderDomain domain = ownedDomain(domainId);
        Instant now = Instant.now();

        if (mockDns) {
            fail(domain, now, "DNS verification disabled by mock-dns; owned DNS token proof is required");
            SenderDomain saved = domainRepository.save(domain);
            recordAttempt(saved, now);
            return saved;
        }

        try {
            boolean hasSpf = checkSpf(domain.getDomainName());
            boolean hasDmarc = checkDmarc(domain.getDomainName());
            String selector = domain.getDkimSelector() != null && !domain.getDkimSelector().isBlank()
                    ? domain.getDkimSelector()
                    : "legent";
            boolean hasDkim = checkDkim(selector, domain.getDomainName());
            boolean hasOwnershipToken = checkOwnershipToken(domain, now);

            domain.setSpfVerified(hasSpf);
            domain.setDmarcVerified(hasDmarc);
            domain.setDkimVerified(hasDkim);
            domain.setOwnershipTokenVerified(hasOwnershipToken);
            domain.setOwnershipTokenVerifiedAt(hasOwnershipToken ? now : null);
            boolean allVerified = hasSpf && hasDkim && hasDmarc && hasOwnershipToken;
            domain.setStatus(allVerified ? SenderDomain.VerificationStatus.VERIFIED : SenderDomain.VerificationStatus.FAILED);
            domain.setIsActive(allVerified);
            domain.setVerificationFailureReason(allVerified ? null : failureReason(hasSpf, hasDkim, hasDmarc, hasOwnershipToken));
            domain.setLastVerifiedAt(now);
        } catch (Exception e) {
            log.error("DNS verification failed for domain {}", domain.getDomainName(), e);
            fail(domain, now, "DNS lookup failed");
        }

        SenderDomain saved = domainRepository.save(domain);
        recordAttempt(saved, now);
        return saved;
    }

    public boolean hasFreshOwnershipProof(SenderDomain domain) {
        if (domain == null || domain.getStatus() != SenderDomain.VerificationStatus.VERIFIED) {
            return false;
        }
        if (!Boolean.TRUE.equals(domain.getOwnershipTokenVerified()) || !Boolean.TRUE.equals(domain.getIsActive())) {
            return false;
        }
        if (!Boolean.TRUE.equals(domain.getSpfVerified())
                || !Boolean.TRUE.equals(domain.getDkimVerified())
                || !Boolean.TRUE.equals(domain.getDmarcVerified())) {
            return false;
        }
        Instant verifiedAt = domain.getOwnershipTokenVerifiedAt();
        Instant lastVerifiedAt = domain.getLastVerifiedAt();
        if (verifiedAt == null || lastVerifiedAt == null) {
            return false;
        }
        Instant freshestAllowed = Instant.now().minus(Math.max(1, proofMaxAgeHours), ChronoUnit.HOURS);
        return !verifiedAt.isBefore(freshestAllowed) && !lastVerifiedAt.isBefore(freshestAllowed);
    }

    private boolean checkSpf(String domainName) throws TextParseException {
        for (String txtStr : dnsTxtResolver.lookupTxt(domainName)) {
            if (txtStr.startsWith("v=spf1")) {
                return true;
            }
        }
        return false;
    }

    private boolean checkDmarc(String domainName) throws TextParseException {
        for (String txtStr : dnsTxtResolver.lookupTxt("_dmarc." + domainName)) {
            if (txtStr.startsWith("v=DMARC1")) {
                return true;
            }
        }
        return false;
    }

    private boolean checkDkim(String selector, String domainName) throws TextParseException {
        for (String txtStr : dnsTxtResolver.lookupTxt(selector + "._domainkey." + domainName)) {
            if (txtStr.contains("v=DKIM1")) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOwnershipToken(SenderDomain domain, Instant now) throws TextParseException {
        if (domain.getVerificationTokenHash() == null || domain.getVerificationTokenHash().isBlank()
                || domain.getVerificationRecordName() == null || domain.getVerificationRecordName().isBlank()
                || domain.getVerificationTokenExpiresAt() == null
                || domain.getVerificationTokenExpiresAt().isBefore(now)) {
            return false;
        }
        List<String> txtRecords = dnsTxtResolver.lookupTxt(domain.getVerificationRecordName());
        for (String record : txtRecords) {
            String value = record == null ? "" : record.trim();
            if (!value.startsWith(TOKEN_PREFIX)) {
                continue;
            }
            String token = value.substring(TOKEN_PREFIX.length()).trim();
            if (constantTimeEquals(hashToken(token), domain.getVerificationTokenHash())) {
                return true;
            }
        }
        return false;
    }

    private SenderDomain ownedDomain(String domainId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        SenderDomain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new NotFoundException("Domain", domainId));
        if (!workspaceId.equals(domain.getWorkspaceId())) {
            throw new org.springframework.security.access.AccessDeniedException("Domain does not belong to active workspace");
        }
        return domain;
    }

    private void fail(SenderDomain domain, Instant now, String reason) {
        domain.setStatus(SenderDomain.VerificationStatus.FAILED);
        domain.setIsActive(false);
        domain.setOwnershipTokenVerified(false);
        domain.setOwnershipTokenVerifiedAt(null);
        domain.setVerificationFailureReason(reason);
        domain.setLastVerifiedAt(now);
    }

    private String failureReason(boolean spf, boolean dkim, boolean dmarc, boolean ownership) {
        StringBuilder reason = new StringBuilder();
        if (!ownership) {
            reason.append("missing exact owned TXT challenge token");
        }
        if (!spf) {
            appendReason(reason, "missing SPF");
        }
        if (!dkim) {
            appendReason(reason, "missing DKIM");
        }
        if (!dmarc) {
            appendReason(reason, "missing DMARC");
        }
        return reason.toString();
    }

    private void appendReason(StringBuilder reason, String value) {
        if (!reason.isEmpty()) {
            reason.append("; ");
        }
        reason.append(value);
    }

    private String challengeRecordName(String domainName) {
        return "_legent-verification." + domainName;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void recordAttempt(SenderDomain domain, Instant now) {
        SenderDomainVerificationHistory history = new SenderDomainVerificationHistory();
        history.setTenantId(domain.getTenantId());
        history.setWorkspaceId(domain.getWorkspaceId());
        history.setSenderDomainId(domain.getId());
        history.setDomainName(domain.getDomainName().toLowerCase(Locale.ROOT));
        history.setStatus(domain.getStatus());
        history.setSpfVerified(Boolean.TRUE.equals(domain.getSpfVerified()));
        history.setDkimVerified(Boolean.TRUE.equals(domain.getDkimVerified()));
        history.setDmarcVerified(Boolean.TRUE.equals(domain.getDmarcVerified()));
        history.setOwnershipTokenVerified(Boolean.TRUE.equals(domain.getOwnershipTokenVerified()));
        history.setVerificationRecordName(domain.getVerificationRecordName());
        history.setFailureReason(domain.getVerificationFailureReason());
        history.setVerifiedAt(now);
        historyRepository.save(history);
    }
}
