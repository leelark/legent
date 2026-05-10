package com.legent.foundation.controller;

import com.legent.foundation.domain.Branding;
import com.legent.foundation.service.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/admin/branding")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class BrandingController {
    private final BrandingService brandingService;

    @GetMapping
    public Branding get() {
        return brandingService.getBranding();
    }

    @PostMapping
    public Branding save(@RequestBody Branding branding) {
        return brandingService.saveBranding(branding);
    }
}
