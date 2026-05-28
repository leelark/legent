package com.legent.automation.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceDataExtensionClientTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    private HttpServer server;
    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void runSqlQueryActivitySendsSignedAutomationIdentity() throws Exception {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer("/api/v1/data-extensions/query-activities/internal", exchange -> {
            captured.set(exchange);
            writeJson(exchange, "{\"success\":true,\"data\":{\"valid\":true,\"rowsRead\":1}}");
        });
        AudienceDataExtensionClient client = new AudienceDataExtensionClient(baseUrl(), INTERNAL_TOKEN);

        Map<String, Object> response = client.runSqlQueryActivity(
                "tenant-1",
                "workspace-1",
                Map.of("sql", "select email from subscribers"));

        assertThat(response).containsEntry("valid", true);
        assertSignedHeaders(
                captured.get(),
                InternalServiceIdentity.ACTION_DATA_EXTENSION_QUERY_ACTIVITY);
    }

    @Test
    void startImportActivitySendsSignedAutomationIdentity() throws Exception {
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        startServer("/api/v1/imports/internal/start", exchange -> {
            captured.set(exchange);
            writeJson(exchange, "{\"success\":true,\"data\":{\"id\":\"import-1\",\"status\":\"QUEUED\"}}");
        });
        AudienceDataExtensionClient client = new AudienceDataExtensionClient(baseUrl(), INTERNAL_TOKEN);

        Map<String, Object> response = client.startImportActivity(
                "tenant-1",
                "workspace-1",
                Map.of("fileName", "contacts.csv"));

        assertThat(response).containsEntry("id", "import-1");
        assertSignedHeaders(
                captured.get(),
                InternalServiceIdentity.ACTION_AUDIENCE_IMPORT_START);
    }

    @Test
    void requestFailsClosedWhenInternalTokenIsPlaceholder() {
        AudienceDataExtensionClient client = new AudienceDataExtensionClient(
                "http://127.0.0.1:1",
                "replace_with_32_plus_character_internal_api_token");

        assertThatThrownBy(() -> client.runSqlQueryActivity("tenant-1", "workspace-1", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    private void assertSignedHeaders(HttpExchange exchange, String action) {
        assertThat(exchange).isNotNull();
        assertThat(exchange.getRequestHeaders().getFirst(AppConstants.HEADER_TENANT_ID)).isEqualTo("tenant-1");
        assertThat(exchange.getRequestHeaders().getFirst(AppConstants.HEADER_WORKSPACE_ID)).isEqualTo("workspace-1");
        assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo(INTERNAL_TOKEN);
        assertThat(exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SERVICE)).isEqualTo("automation-service");
        assertTrue(InternalServiceIdentity.matches(
                INTERNAL_TOKEN,
                exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SERVICE),
                java.util.Set.of("automation-service"),
                "tenant-1",
                "workspace-1",
                action,
                exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP),
                exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SIGNATURE)));
    }

    private void startServer(String path, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler::handle);
        executorService = Executors.newSingleThreadExecutor();
        server.setExecutor(executorService);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
