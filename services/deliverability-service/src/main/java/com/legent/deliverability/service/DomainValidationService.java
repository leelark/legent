package com.legent.deliverability.service;

import com.legent.deliverability.domain.DomainConfig;
import com.legent.deliverability.repository.DomainConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DomainValidationService {
    private final DomainConfigRepository domainRepo;

    public DomainConfig validateDomain(String domain) {
        // Simulate SPF/DKIM/DMARC validation (replace with real DNS checks)
        DomainConfig config = domainRepo.findByDomain(domain);
        if (config == null) return null;
        config.setSpfStatus("PASS");
        config.setDkimStatus("PASS");
        config.setDmarcStatus("PASS");
        config.setLastChecked(Instant.now());
        config.setStatus("VERIFIED");
        return domainRepo.save(config);
    }
}
