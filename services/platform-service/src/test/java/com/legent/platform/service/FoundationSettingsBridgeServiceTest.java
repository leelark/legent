package com.legent.platform.service;

import com.legent.platform.domain.TenantConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundationSettingsBridgeServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loadTenantConfigRelaysCallerBearerTokenToFoundation() {
        List<ClientRequest> captured = new ArrayList<>();
        FoundationSettingsBridgeService service = serviceWithResponses(captured,
                """
                        {"data":[
                          {"key":"platform.theme_color","value":"#123456"},
                          {"key":"platform.timezone","value":"Asia/Calcutta"}
                        ]}
                        """);
        setBearerRequest("caller-token");

        TenantConfig config = service.loadTenantConfig("tenant-1", "workspace-1");

        assertEquals("#123456", config.getThemeColor());
        assertEquals("Asia/Calcutta", config.getTimezone());
        assertEquals(1, captured.size());
        ClientRequest request = captured.get(0);
        assertEquals(HttpMethod.GET, request.method());
        assertEquals("Bearer caller-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("tenant-1", request.headers().getFirst("X-Tenant-Id"));
        assertEquals("workspace-1", request.headers().getFirst("X-Workspace-Id"));
    }

    @Test
    void saveTenantConfigRelaysCookieTokenOnEveryFoundationCall() {
        List<ClientRequest> captured = new ArrayList<>();
        FoundationSettingsBridgeService service = serviceWithResponses(captured,
                "{\"data\":{}}",
                "{\"data\":{}}",
                "{\"data\":{}}",
                "{\"data\":{}}",
                "{\"data\":[]}");
        setCookieRequest("cookie-token");

        TenantConfig config = new TenantConfig();
        config.setThemeColor("#334455");
        config.setTimezone("UTC");
        config.setLogoUrl("https://cdn.example/logo.png");
        config.setFeaturesJson("{}");

        service.saveTenantConfig("tenant-1", "workspace-1", config);

        assertEquals(5, captured.size());
        assertEquals(4, captured.stream().filter(request -> request.method() == HttpMethod.POST).count());
        assertEquals(1, captured.stream().filter(request -> request.method() == HttpMethod.GET).count());
        for (ClientRequest request : captured) {
            assertEquals("Bearer cookie-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            assertEquals("tenant-1", request.headers().getFirst("X-Tenant-Id"));
            assertEquals("workspace-1", request.headers().getFirst("X-Workspace-Id"));
        }
    }

    @Test
    void bridgeFailsClosedWithoutCallerAuthorization() {
        List<ClientRequest> captured = new ArrayList<>();
        FoundationSettingsBridgeService service = serviceWithResponses(captured, "{\"data\":[]}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThrows(IllegalStateException.class,
                () -> service.loadTenantConfig("tenant-1", "workspace-1"));
        assertEquals(0, captured.size());
    }

    private FoundationSettingsBridgeService serviceWithResponses(List<ClientRequest> captured, String... responses) {
        AtomicInteger index = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.add(request);
                    int responseIndex = Math.min(index.getAndIncrement(), responses.length - 1);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(responses[responseIndex])
                            .build());
                })
                .build();
        FoundationSettingsBridgeService service = new FoundationSettingsBridgeService(webClient);
        ReflectionTestUtils.setField(service, "foundationBaseUrl", "http://foundation-service:8081/api/v1");
        return service;
    }

    private void setBearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setCookieRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("legent_token", token));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
