package com.legent.content.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalizationTokenServiceTest {

    @Test
    void extractsOnlyRegisteredTokenCandidates() {
        PersonalizationTokenService service = new PersonalizationTokenService(null, new EmailContentValidationService());

        Set<String> keys = service.extractTokenKeys("Hi {{firstName}}, {{snippet.footer}} {{dynamic.main}} {{brand.footer}} {{order.id}}");

        assertEquals(Set.of("firstName", "order.id"), keys);
    }
}
