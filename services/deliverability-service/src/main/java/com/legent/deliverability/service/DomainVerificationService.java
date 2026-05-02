package com.legent.deliverability.service;

import java.time.Instant;

import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.SenderDomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import com.legent.security.TenantContext;
import com.legent.common.exception.NotFoundException;


@Slf4j
@Service
@RequiredArgsConstructor

public class DomainVerificationService {

    private final SenderDomainRepository domainRepository;
    
    @Value("${legent.deliverability.mock-dns:false}")
    private boolean mockDns;



    public SenderDomain verifyDomain(String domainId) {
        String tenantId = TenantContext.getTenantId();
        SenderDomain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new NotFoundException("Domain", domainId));

        if (mockDns) {
            // Short-circuit for MVP local testing
            domain.setSpfVerified(true);
            domain.setDkimVerified(true);
            domain.setDmarcVerified(true);
            domain.setStatus(SenderDomain.VerificationStatus.VERIFIED);
            domain.setIsActive(true);
            domain.setLastVerifiedAt(Instant.now());
            return domainRepository.save(domain);
        }

        // Real DNS checking logic
        try {
            boolean hasSpf = checkSpf(domain.getDomainName());
            boolean hasDmarc = checkDmarc(domain.getDomainName());
            String selector = domain.getDkimSelector() != null && !domain.getDkimSelector().isBlank() ? domain.getDkimSelector() : "legent";
            boolean hasDkim = checkDkim(selector, domain.getDomainName());

            domain.setSpfVerified(hasSpf);
            domain.setDmarcVerified(hasDmarc);
            domain.setDkimVerified(hasDkim);
            // All three checks (SPF, DKIM, DMARC) must pass for VERIFIED status
            boolean allVerified = hasSpf && hasDkim && hasDmarc;
            domain.setStatus(allVerified ? SenderDomain.VerificationStatus.VERIFIED : SenderDomain.VerificationStatus.FAILED);
            domain.setIsActive(allVerified); // Require all three for active status
            domain.setLastVerifiedAt(Instant.now());

        } catch (Exception e) {
            log.error("DNS verification failed for domain {}", domain.getDomainName(), e);
            // AUDIT-017: Fail closed - set FAILED status on DNS errors
            domain.setStatus(SenderDomain.VerificationStatus.FAILED);
            domain.setLastVerifiedAt(Instant.now());
        }

        return domainRepository.save(domain);
    }

    private boolean checkSpf(String domainName) throws TextParseException {
        Lookup lookup = new Lookup(domainName, Type.TXT);
        Record[] records = lookup.run();
        if (records != null) {
            for (Record record : records) {
                TXTRecord txt = (TXTRecord) record;
                String txtStr = String.join(" ", txt.getStrings());
                if (txtStr.startsWith("v=spf1")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkDmarc(String domainName) throws TextParseException {
        Lookup lookup = new Lookup("_dmarc." + domainName, Type.TXT);
        Record[] records = lookup.run();
        if (records != null) {
            for (Record record : records) {
                TXTRecord txt = (TXTRecord) record;
                String txtStr = String.join(" ", txt.getStrings());
                if (txtStr.startsWith("v=DMARC1")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkDkim(String selector, String domainName) throws TextParseException {
        Lookup lookup = new Lookup(selector + "._domainkey." + domainName, Type.TXT);
        Record[] records = lookup.run();
        if (records != null) {
            for (Record record : records) {
                TXTRecord txt = (TXTRecord) record;
                String txtStr = String.join(" ", txt.getStrings());
                if (txtStr.contains("v=DKIM1")) {
                    return true;
                }
            }
        }
        return false;
    }
}
