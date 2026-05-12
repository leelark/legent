package com.legent.delivery.client;

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
            writeJson(exchange, 200, """
                    {"data":{"subject":"Launch","htmlBody":"<p>Hello</p>","textBody":"Hello"}}
                    """);
        });

        ContentServiceClient client = new ContentServiceClient(baseUrl(), 5, 60);

        Map<String, String> first = client.fetchCampaignContent("campaign-1");
        Map<String, String> second = client.fetchCampaignContent("campaign-1");

        assertEquals("Launch", first.get("subject"));
        assertEquals(first, second);
        assertEquals(1, hits.get());
    }

    @Test
    void fetchCampaignContent_ReturnsEmptyMapOnHttpError() throws Exception {
        startServer(exchange -> writeJson(exchange, 404, "{\"error\":\"missing\"}"));

        ContentServiceClient client = new ContentServiceClient(baseUrl(), 5, 60);

        assertTrue(client.fetchCampaignContent("missing-campaign").isEmpty());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/content/campaign/campaign-1", handler::handle);
        server.createContext("/api/v1/content/campaign/missing-campaign", handler::handle);
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
