package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentServiceClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-123";

    private HttpServer server;
    private ContentServiceClient client;
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.clear();
        responseBody = """
                {"success":true,"data":{"subject":"Rendered","htmlBody":"<p>Hello</p>","textBody":"Hello"}}
                """;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        client = new ContentServiceClient("http://localhost:" + server.getAddress().getPort(), INTERNAL_TOKEN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void renderTemplateSendsTenantWorkspaceEnvironmentAndInternalToken() {
        TenantContext.setEnvironmentId("prod");
        TenantContext.setRequestId("request-1");
        TenantContext.setCorrelationId("correlation-1");

        ContentServiceClient.RenderedContent rendered = client.renderTemplate(
                "tenant-1",
                "workspace-1",
                "template-1",
                Map.of("firstName", "Asha"));

        assertEquals("Rendered", rendered.subject());
        assertEquals("<p>Hello</p>", rendered.htmlBody());
        CapturedRequest request = capturedRequest.get();
        assertEquals("POST", request.method());
        assertEquals("/api/v1/content/template-1/render/internal", request.path());
        assertEquals("tenant-1", request.header(AppConstants.HEADER_TENANT_ID));
        assertEquals("workspace-1", request.header(AppConstants.HEADER_WORKSPACE_ID));
        assertEquals("prod", request.header(AppConstants.HEADER_ENVIRONMENT_ID));
        assertEquals("request-1", request.header(AppConstants.HEADER_REQUEST_ID));
        assertEquals("correlation-1", request.header(AppConstants.HEADER_CORRELATION_ID));
        assertEquals(INTERNAL_TOKEN, request.header("X-Internal-Token"));
        assertTrue(request.body().contains("Asha"));
    }

    @Test
    void renderTemplateFailsClosedBeforeHttpWhenWorkspaceIsMissing() {
        ContentServiceClient.ContentServiceException exception = assertThrows(
                ContentServiceClient.ContentServiceException.class,
                () -> client.renderTemplate("tenant-1", " ", "template-1", Map.of()));

        assertTrue(exception.getMessage().contains("workspaceId"));
        assertNull(capturedRequest.get());
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        capturedRequest.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT),
                                Map.Entry::getValue)),
                body));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record CapturedRequest(String method, String path, Map<String, List<String>> headers, String body) {
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}
