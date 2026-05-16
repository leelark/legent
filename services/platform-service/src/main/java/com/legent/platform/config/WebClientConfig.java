package com.legent.platform.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(WebhookOutboundProperties.class)
public class WebClientConfig {

    private static final String WEBHOOK_ENDPOINT_LABEL = "webhook endpoint";

    @Bean
    @Primary
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = baseHttpClient();

        return builder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebhookWebClient webhookWebClient(
            WebClient.Builder builder,
            WebhookOutboundProperties properties,
            Environment environment) {
        HttpClient httpClient = baseHttpClient();
        if (properties.hasProxyUrl()) {
            httpClient = configureProxy(httpClient, properties.getProxyUrl());
        } else {
            if (properties.isRequireProxyInProduction() && isProductionProfile(environment)) {
                throw new IllegalStateException(
                        "Outbound webhook direct dispatch is disabled in production; configure "
                                + "legent.webhooks.outbound.proxy-url");
            }
            httpClient = httpClient.resolver(new PublicAddressValidatingAddressResolverGroup(
                    DefaultAddressResolverGroup.INSTANCE,
                    WEBHOOK_ENDPOINT_LABEL));
        }

        return new WebhookWebClient(builder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build());
    }

    private HttpClient baseHttpClient() {
        return HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))
                .compress(true);
    }

    private HttpClient configureProxy(HttpClient httpClient, String rawProxyUrl) {
        ProxySettings proxySettings = parseProxySettings(rawProxyUrl);
        return httpClient.proxy(proxy -> proxy
                .type(ProxyProvider.Proxy.HTTP)
                .host(proxySettings.host())
                .port(proxySettings.port()));
    }

    private ProxySettings parseProxySettings(String rawProxyUrl) {
        URI proxyUri;
        try {
            proxyUri = URI.create(rawProxyUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url is not a valid URI", ex);
        }

        String scheme = proxyUri.getScheme() == null
                ? null
                : proxyUri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme)) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url must use http");
        }
        if (proxyUri.getUserInfo() != null) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url must not include user info");
        }
        if (proxyUri.getHost() == null || proxyUri.getHost().isBlank()) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url must include a host");
        }
        String path = proxyUri.getRawPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url must not include a path");
        }
        if (proxyUri.getRawQuery() != null || proxyUri.getRawFragment() != null) {
            throw new IllegalStateException("legent.webhooks.outbound.proxy-url must not include query or fragment");
        }
        int port = proxyUri.getPort() == -1 ? 80 : proxyUri.getPort();
        return new ProxySettings(proxyUri.getHost(), port);
    }

    private boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "prod".equals(profile) || "production".equals(profile));
    }

    private record ProxySettings(String host, int port) {
    }
}
