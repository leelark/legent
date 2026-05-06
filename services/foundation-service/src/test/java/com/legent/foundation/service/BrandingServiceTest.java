package com.legent.foundation.service;

import com.legent.foundation.domain.Branding;
import com.legent.foundation.dto.AdminSettingsDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class BrandingServiceTest {
    @Test
    void saveAndGetBranding() {
        var settingsService = Mockito.mock(AdminSettingsService.class);
        var branding = new Branding();
        branding.setName("Test");
        branding.setLogoUrl("https://logo.png");
        branding.setPrimaryColor("#112233");
        branding.setSecondaryColor("#445566");

        Mockito.when(settingsService.apply(Mockito.any())).thenReturn(new AdminSettingsDto.Entry());
        Mockito.when(settingsService.listSettings(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(java.util.List.of(
                AdminSettingsDto.Entry.builder().key("branding.name").value("Test").build(),
                AdminSettingsDto.Entry.builder().key("branding.logo_url").value("https://logo.png").build(),
                AdminSettingsDto.Entry.builder().key("branding.primary_color").value("#112233").build(),
                AdminSettingsDto.Entry.builder().key("branding.secondary_color").value("#445566").build()
        ));

        var service = new BrandingService(settingsService);
        assertEquals("Test", service.saveBranding(branding).getName());
        assertEquals("Test", service.getBranding().getName());
    }
}
