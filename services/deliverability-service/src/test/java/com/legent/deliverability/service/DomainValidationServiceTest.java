package com.legent.deliverability.service;

import com.legent.deliverability.domain.DomainConfig;
import com.legent.deliverability.repository.DomainConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class DomainValidationServiceTest {
    @Test
    void validateDomain_setsAllPassAndVerified() {
        var repo = Mockito.mock(DomainConfigRepository.class);
        var config = new DomainConfig();
        config.setDomain("example.com");
        Mockito.when(repo.findByDomain("example.com")).thenReturn(config);
        Mockito.when(repo.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        var service = new DomainValidationService(repo);
        var result = service.validateDomain("example.com");
        assertEquals("PASS", result.getSpfStatus());
        assertEquals("PASS", result.getDkimStatus());
        assertEquals("PASS", result.getDmarcStatus());
        assertEquals("VERIFIED", result.getStatus());
    }
}
