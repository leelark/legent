package com.legent.content.service;

import com.legent.content.dto.EmailStudioDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailContentValidationServiceTest {

    private final EmailContentValidationService service = new EmailContentValidationService();

    @Test
    void rejectsInteractiveEmailAndUnsafeScript() {
        EmailStudioDto.ValidationResponse response = service.validate(
                "<html amp4email><body><script>alert(1)</script><form><input name=\"email\"></form><amp-img></amp-img></body></html>",
                null);

        assertEquals("FAIL", response.getStatus());
        assertTrue(response.getAmpUnsupported());
        assertTrue(response.getErrors().stream().anyMatch(error -> error.contains("AMP")));
        assertTrue(response.getErrors().stream().anyMatch(error -> error.contains("Unsupported email tag")));
    }

    @Test
    void sanitizesUnsafeCssAndJavascriptUrls() {
        String sanitized = service.sanitize("<a href=\"javascript:alert(1)\" style=\"color:red;behavior:url(x)\">x</a>");

        assertFalse(sanitized.contains("javascript:"));
        assertFalse(sanitized.contains("behavior"));
        assertTrue(sanitized.contains("color:red"));
    }

    @Test
    void landingPageSanitizerAllowsFormsButRemovesScripts() {
        String sanitized = service.sanitizeLandingPage("<form><input name=\"email\"><script>alert(1)</script></form>");

        assertTrue(sanitized.contains("<form>"));
        assertTrue(sanitized.contains("<input"));
        assertFalse(sanitized.contains("<script"));
    }
}
