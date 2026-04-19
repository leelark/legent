package com.legent.foundation.service;

import com.legent.foundation.domain.Branding;
import com.legent.foundation.repository.BrandingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandingService {
    private final BrandingRepository repo;

    public Branding getBranding() {
        return repo.findAll().stream().findFirst().orElse(null);
    }

    public Branding saveBranding(Branding branding) {
        return repo.save(branding);
    }
}
