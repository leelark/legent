package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
import com.legent.common.security.OutboundUrlGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class CustomApiProviderAdapter extends ApiProviderAdapterSupport implements ProviderAdapter {

    private final WebClient webClient;

    public CustomApiProviderAdapter(CredentialEncryptionService credentialEncryptionService) {
        super(credentialEncryptionService);
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String getProviderType() {
        return "CUSTOM_API";
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody, Map<String, String> metadata, SmtpProvider config)
            throws ProviderDispatchException {
        String token = resolveSecret(config);
        URI endpoint = OutboundUrlGuard.requirePublicHttpsUri(resolveConfiguredBaseUri(config).toString(), "custom API provider endpoint");
        String fromEmail = resolveFromEmail(metadata, config, to);

        Map<String, Object> payload = Map.of(
                "to", to,
                "subject", subject,
                "htmlBody", htmlBody,
                "fromEmail", fromEmail,
                "metadata", metadata == null ? Map.of() : metadata
        );

        try {
            webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> response.createException()
                                            .map(ex -> new RuntimeException("Custom API send failed " + response.statusCode() + " " + body, ex))))
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(15))
                    .block();
        } catch (WebClientResponseException ex) {
            throw dispatchFailure("Custom API send failed: " + ex.getResponseBodyAsString(), ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            log.warn("Custom API dispatch error: {}", ex.getMessage());
            throw new ProviderDispatchException("Custom API dispatch failed: " + ex.getMessage(), false, ex);
        }
    }
}
