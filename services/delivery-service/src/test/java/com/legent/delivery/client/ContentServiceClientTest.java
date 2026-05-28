package com.legent.delivery.client;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentServiceClientTest {

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
    void fetchCampaignContent_CachesSuccessfulResultWithoutScheduledExecutors() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        startServer(exchange -> {
            hits.incrementAndGet();
            assertEquals("tenant-1", exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            assertEquals("workspace-1", exchange.getRequestHeaders().getFirst("X-Workspace-Id"));
            assertEquals(INTERNAL_TOKEN, exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            assertEquals("delivery-service", exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SERVICE));
            assertTrue(InternalServiceIdentity.matches(
                    INTERNAL_TOKEN,
                    exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                    exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SERVICE),
                    java.util.Set.of("delivery-service"),
                    "tenant-1",
                    "workspace-1",
                    InternalServiceIdentity.scopedAction(
                            InternalServiceIdentity.ACTION_CONTENT_RENDERED_SNAPSHOT_READ,
                            "cr_ref"),
                    exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP),
                    exchange.getRequestHeaders().getFirst(InternalServiceIdentity.HEADER_SIGNATURE)));
            writeJson(exchange, 200, """
                    {"data":{"tenantId":"tenant-1","workspaceId":"workspace-1","campaignId":"campaign-1","messageId":"message-1","referenceId":"cr_ref","subject":"Launch","htmlBody":"<p>Hello</p>","textBody":"Hello"}}
                    """);
        });

        ContentServiceClient client = new ContentServiceClient(baseUrl(), 30, 60, INTERNAL_TOKEN);

        Map<String, String> first = client.fetchRenderedContent("tenant-1", "workspace-1", "cr_ref");
        Map<String, String> second = client.fetchRenderedContent("tenant-1", "workspace-1", "cr_ref");

        assertEquals("Launch", first.get("subject"));
        assertEquals("tenant-1", first.get("tenantId"));
        assertEquals("workspace-1", first.get("workspaceId"));
        assertEquals("cr_ref", first.get("referenceId"));
        assertEquals(first, second);
        assertEquals(1, hits.get());
    }

    @Test
    void constructorRejectsDocumentedPlaceholderInternalToken() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                new ContentServiceClient(
                        "http://127.0.0.1:1",
                        5,
                        60,
                        "replace_with_32_plus_character_internal_api_token"));
    }

    @Test
    void fetchCampaignContent_ReturnsEmptyMapOnHttpError() throws Exception {
        startServer(exchange -> writeJson(exchange, 404, "{\"error\":\"missing\"}"));

        ContentServiceClient client = new ContentServiceClient(baseUrl(), 30, 60, INTERNAL_TOKEN);

        assertTrue(client.fetchRenderedContent("tenant-1", "workspace-1", "missing-reference").isEmpty());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/content/rendered-content/cr_ref/internal", handler::handle);
        server.createContext("/api/v1/content/rendered-content/missing-reference/internal", handler::handle);
        executorService = Executors.newSingleThreadExecutor();
        server.setExecutor(executorService);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
