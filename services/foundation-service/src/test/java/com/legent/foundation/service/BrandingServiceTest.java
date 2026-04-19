package com.legent.foundation.service;

import com.legent.foundation.domain.Branding;
import com.legent.foundation.repository.BrandingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class BrandingServiceTest {
    @Test
    void saveAndGetBranding() {
        var repo = Mockito.mock(BrandingRepository.class);
        var branding = new Branding();
        branding.setName("Test");
        Mockito.when(repo.save(Mockito.any())).thenReturn(branding);
        Mockito.when(repo.findAll()).thenReturn(java.util.List.of(branding));
        var service = new BrandingService(repo);
        assertEquals("Test", service.saveBranding(branding).getName());
        assertEquals("Test", service.getBranding().getName());
    }
}
