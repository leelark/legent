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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentServiceClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ContentServiceClient client;
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.clear();
        responseBody = """
                {"success":true,"data":{"subject":"Rendered","htmlBody":"<p>Hello</p>","textBody":"Hello"}}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
        client = new ContentServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), INTERNAL_TOKEN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            try {
                serverExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
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
    void constructorRejectsDocumentedPlaceholderInternalToken() {
        assertThrows(IllegalStateException.class, () ->
                new ContentServiceClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        "replace_with_32_plus_character_internal_api_token"));
    }

    @Test
    void renderTemplateFailsClosedBeforeHttpWhenWorkspaceIsMissing() {
        ContentServiceClient.ContentServiceException exception = assertThrows(
                ContentServiceClient.ContentServiceException.class,
                () -> client.renderTemplate("tenant-1", " ", "template-1", Map.of()));

        assertTrue(exception.getMessage().contains("workspaceId"));
        assertNull(capturedRequest.get());
    }

    @Test
    void getSendGovernancePolicySendsTenantWorkspaceAndInternalToken() {
        responseBody = """
                {"success":true,"data":{"id":"policy-1","policyKey":"promo.default","classification":"COMMERCIAL","commercial":true,"senderProfileId":"sender-1","deliveryProfileId":"delivery-1","sendingDomain":"example.com","providerId":"provider-1","unsubscribePolicy":"REQUIRED","suppressionRequired":true,"consentRequired":false,"trackingAllowed":true,"sendLogRetentionDays":365,"active":true}}
                """;

        ContentServiceClient.SendGovernancePolicySummary policy = client.getSendGovernancePolicy(
                "tenant-1",
                "workspace-1",
                "policy-1");

        assertEquals("promo.default", policy.policyKey());
        assertEquals("COMMERCIAL", policy.classification());
        assertTrue(policy.suppressionRequired());
        CapturedRequest request = capturedRequest.get();
        assertEquals("GET", request.method());
        assertEquals("/api/v1/content/send-governance-policies/policy-1/internal", request.path());
        assertEquals("tenant-1", request.header(AppConstants.HEADER_TENANT_ID));
        assertEquals("workspace-1", request.header(AppConstants.HEADER_WORKSPACE_ID));
        assertEquals(INTERNAL_TOKEN, request.header("X-Internal-Token"));
    }

    @Test
    void getSendGovernancePolicyFailsClosedBeforeHttpWhenPolicyIdIsMissing() {
        ContentServiceClient.ContentServiceException exception = assertThrows(
                ContentServiceClient.ContentServiceException.class,
                () -> client.getSendGovernancePolicy("tenant-1", "workspace-1", " "));

        assertTrue(exception.getMessage().contains("policyId"));
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
