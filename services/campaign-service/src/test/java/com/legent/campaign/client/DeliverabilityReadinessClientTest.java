package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.security.JwtTokenProvider;
import com.legent.security.TenantContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class DeliverabilityReadinessClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private DeliverabilityReadinessClient client;
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.clear();
        responseBody = """
                {"success":true,"data":{"total":125,"complaints":10,"hardBounces":50,"unsubscribes":65,"generatedAt":"2026-05-24T14:00:00Z"}}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 86_400_000);
        ServiceAuthTokenProvider tokenProvider = new ServiceAuthTokenProvider(jwtTokenProvider, Duration.ofMinutes(2));
        client = new DeliverabilityReadinessClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                tokenProvider,
                INTERNAL_TOKEN);
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
    void suppressionHealthReadsAggregateHistoryInsteadOfBoundedListSample() {
        TenantContext.setEnvironmentId("prod");
        TenantContext.setRequestId("request-1");

        DeliverabilityReadinessClient.SuppressionHealth health = client.suppressionHealth("tenant-1", "workspace-1");

        assertThat(health.total()).isEqualTo(125);
        assertThat(health.complaints()).isEqualTo(10);
        assertThat(health.hardBounces()).isEqualTo(50);
        assertThat(health.unsubscribes()).isEqualTo(65);
        assertThat(health.generatedAt()).isEqualTo(Instant.parse("2026-05-24T14:00:00Z"));
        CapturedRequest request = capturedRequest.get();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/v1/deliverability/suppressions/internal/history");
        assertThat(request.header(AppConstants.HEADER_TENANT_ID)).isEqualTo("tenant-1");
        assertThat(request.header(AppConstants.HEADER_WORKSPACE_ID)).isEqualTo("workspace-1");
        assertThat(request.header(AppConstants.HEADER_ENVIRONMENT_ID)).isEqualTo("prod");
        assertThat(request.header(AppConstants.HEADER_REQUEST_ID)).isEqualTo("request-1");
        assertThat(request.header("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
        assertThat(request.header(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
        assertThat(request.header(InternalServiceIdentity.HEADER_SERVICE)).isEqualTo("campaign-service");
        assertThat(InternalServiceIdentity.matches(
                INTERNAL_TOKEN,
                request.header("X-Internal-Token"),
                request.header(InternalServiceIdentity.HEADER_SERVICE),
                java.util.Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ,
                request.header(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP),
                request.header(InternalServiceIdentity.HEADER_SIGNATURE))).isTrue();
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
