package com.legent.foundation.service;

import com.legent.foundation.domain.Branding;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandingService {
    private final AdminSettingsService adminSettingsService;

    public Branding getBranding() {
        Branding branding = new Branding();
        branding.setName(resolve("branding.name", "Legent"));
        branding.setLogoUrl(resolve("branding.logo_url", ""));
        branding.setPrimaryColor(resolve("branding.primary_color", "#0B6E4F"));
        branding.setSecondaryColor(resolve("branding.secondary_color", "#F4A261"));
        return branding;
    }

    public Branding saveBranding(Branding branding) {
        String workspaceId = TenantContext.getWorkspaceId();
        apply("branding.name", branding.getName(), "template", workspaceId);
        apply("branding.logo_url", branding.getLogoUrl(), "template", workspaceId);
        apply("branding.primary_color", branding.getPrimaryColor(), "template", workspaceId);
        apply("branding.secondary_color", branding.getSecondaryColor(), "template", workspaceId);
        return getBranding();
    }

    private void apply(String key, String value, String module, String workspaceId) {
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey(key);
        request.setValue(value == null ? "" : value);
        request.setModule(module);
        request.setCategory("TEMPLATE");
        request.setType("STRING");
        request.setScope("WORKSPACE");
        request.setWorkspaceId(workspaceId);
        adminSettingsService.apply(request);
    }

    private String resolve(String key, String fallback) {
        try {
            return adminSettingsService.listSettings(null, null, null).stream()
                    .filter(entry -> key.equals(entry.getKey()))
                    .findFirst()
                    .map(AdminSettingsDto.Entry::getValue)
                    .orElse(fallback);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
