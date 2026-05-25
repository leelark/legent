package com.legent.audience.client;

import com.legent.common.security.InternalServiceIdentity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverabilityServiceClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private DeliverabilityServiceClient client;
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = """
                {"success":true,"data":{"checkedCount":2,"suppressedCount":1,"suppressedEmails":["User@Example.COM"]}}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/", this::handleRequest);
        server.start();
        client = new DeliverabilityServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), INTERNAL_TOKEN);
    }

    @AfterEach
    void tearDown() {
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
    void bulkCheckPostsCandidateEmailsAndParsesSuppressedEmails() {
        Set<String> result = client.checkSuppressedEmails(
                "tenant-1",
                "workspace-1",
                List.of(" User@Example.com ", "other@example.com", "USER@example.com"));

        assertThat(result).containsExactly("user@example.com");
        CapturedRequest request = capturedRequest.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/deliverability/suppressions/internal/check");
        assertThat(request.header("X-Tenant-Id")).isEqualTo("tenant-1");
        assertThat(request.header("X-Workspace-Id")).isEqualTo("workspace-1");
        assertThat(request.header("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
        assertThat(request.header(InternalServiceIdentity.HEADER_SERVICE)).isEqualTo("audience-service");
        assertThat(InternalServiceIdentity.matches(
                INTERNAL_TOKEN,
                request.header("X-Internal-Token"),
                request.header(InternalServiceIdentity.HEADER_SERVICE),
                java.util.Set.of("audience-service"),
                "tenant-1",
                "workspace-1",
                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK,
                request.header(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP),
                request.header(InternalServiceIdentity.HEADER_SIGNATURE))).isTrue();
        assertThat(request.body()).contains("\"emails\":[\"user@example.com\",\"other@example.com\"]");
    }

    @Test
    void emptyCandidateListShortCircuitsWithoutHttp() {
        Set<String> result = client.checkSuppressedEmails("tenant-1", "workspace-1", Arrays.asList(" ", null));

        assertThat(result).isEmpty();
        assertThat(capturedRequest.get()).isNull();
    }

    @Test
    void errorStatusThrowsSuppressionCheckException() {
        responseStatus.set(503);
        responseBody = "{\"success\":false}";

        assertThatThrownBy(() -> client.checkSuppressedEmails("tenant-1", "workspace-1", List.of("a@example.com")))
                .isInstanceOf(DeliverabilityServiceClient.SuppressionCheckException.class)
                .hasMessageContaining("Failed to check suppressed emails");
    }

    @Test
    void malformedResponseThrowsSuppressionCheckException() {
        responseBody = "{\"success\":true,\"data\":{}}";

        assertThatThrownBy(() -> client.checkSuppressedEmails("tenant-1", "workspace-1", List.of("a@example.com")))
                .isInstanceOf(DeliverabilityServiceClient.SuppressionCheckException.class)
                .hasMessageContaining("Failed to check suppressed emails");
    }

    @Test
    void constructorRejectsPlaceholderInternalToken() {
        assertThatThrownBy(() ->
                new DeliverabilityServiceClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        "replace_with_32_plus_character_internal_api_token"))
                .isInstanceOf(IllegalStateException.class);
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
        exchange.sendResponseHeaders(responseStatus.get(), response.length);
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
