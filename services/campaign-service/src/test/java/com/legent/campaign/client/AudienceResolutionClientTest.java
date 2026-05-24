package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
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

class AudienceResolutionClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private AudienceResolutionClient client;
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.clear();
        responseBody = """
                {"success":true,"data":{"tenantId":"tenant-1","workspaceId":"workspace-1","campaignId":"campaign-1","jobId":"job-1","chunkId":"job-1:audience:0","chunkIndex":0,"chunkSize":1,"totalChunks":1,"totalResolvedSubscribers":1,"isLastChunk":true,"subscribers":[{"email":"one@example.com","subscriberId":"sub-1"}]}}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
        client = new AudienceResolutionClient("http://127.0.0.1:" + server.getAddress().getPort(), INTERNAL_TOKEN);
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
    void readChunkSendsTenantWorkspaceContextAndInternalToken() {
        TenantContext.setEnvironmentId("prod");
        TenantContext.setRequestId("request-1");
        TenantContext.setCorrelationId("correlation-1");

        AudienceResolutionClient.ResolvedAudienceChunk chunk = client.readChunk(
                "tenant-1",
                "workspace-1",
                "job-1",
                "job-1:audience:0");

        assertEquals("job-1:audience:0", chunk.chunkId());
        assertEquals(0, chunk.chunkIndex());
        assertTrue(chunk.isLastChunk());
        assertEquals(List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")), chunk.subscribers());
        CapturedRequest request = capturedRequest.get();
        assertEquals("GET", request.method());
        assertEquals("/api/v1/audience-resolution-chunks/job-1:audience:0/internal", request.path());
        assertEquals("jobId=job-1", request.query());
        assertEquals("tenant-1", request.header(AppConstants.HEADER_TENANT_ID));
        assertEquals("workspace-1", request.header(AppConstants.HEADER_WORKSPACE_ID));
        assertEquals("prod", request.header(AppConstants.HEADER_ENVIRONMENT_ID));
        assertEquals("request-1", request.header(AppConstants.HEADER_REQUEST_ID));
        assertEquals("correlation-1", request.header(AppConstants.HEADER_CORRELATION_ID));
        assertEquals(INTERNAL_TOKEN, request.header("X-Internal-Token"));
        assertEquals("campaign-service", request.header(InternalServiceIdentity.HEADER_SERVICE));
        assertTrue(InternalServiceIdentity.matches(
                INTERNAL_TOKEN,
                request.header("X-Internal-Token"),
                request.header(InternalServiceIdentity.HEADER_SERVICE),
                java.util.Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                AudienceResolutionClient.chunkReadAction("job-1", "job-1:audience:0"),
                request.header(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP),
                request.header(InternalServiceIdentity.HEADER_SIGNATURE)));
    }

    @Test
    void constructorRejectsDocumentedPlaceholderInternalToken() {
        assertThrows(IllegalStateException.class, () ->
                new AudienceResolutionClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        "replace_with_32_plus_character_internal_api_token"));
    }

    @Test
    void readChunkFailsClosedBeforeHttpWhenScopeIsMissing() {
        AudienceResolutionClient.AudienceResolutionClientException exception = assertThrows(
                AudienceResolutionClient.AudienceResolutionClientException.class,
                () -> client.readChunk("tenant-1", " ", "job-1", "chunk-1"));

        assertTrue(exception.getMessage().contains("workspaceId"));
        assertNull(capturedRequest.get());
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        capturedRequest.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
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

    private record CapturedRequest(String method,
                                   String path,
                                   String query,
                                   Map<String, List<String>> headers,
                                   String body) {
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}
