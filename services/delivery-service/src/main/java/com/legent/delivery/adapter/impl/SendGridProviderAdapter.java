package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SendGridProviderAdapter extends ApiProviderAdapterSupport implements ProviderAdapter {

    private final WebClient webClient;

    public SendGridProviderAdapter(CredentialEncryptionService credentialEncryptionService) {
        super(credentialEncryptionService);
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String getProviderType() {
        return "SENDGRID";
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody, Map<String, String> metadata, SmtpProvider config)
            throws ProviderDispatchException {
        String apiKey = resolveSecret(config);
        URI baseUri = resolveBaseUri(config, "https://api.sendgrid.com");
        String fromEmail = resolveFromEmail(metadata, config, to);
        String fromName = metadata != null ? metadata.get("From-Name") : null;

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", fromName != null && !fromName.isBlank()
                        ? Map.of("email", fromEmail, "name", fromName)
                        : Map.of("email", fromEmail),
                "subject", subject,
                "content", List.of(Map.of("type", "text/html", "value", htmlBody))
        );

        try {
            webClient.post()
                    .uri(baseUri.resolve("/v3/mail/send"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> response.createException()
                                            .map(ex -> new RuntimeException("SendGrid send failed " + response.statusCode() + " " + body, ex))))
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(15))
                    .block();
        } catch (WebClientResponseException ex) {
            throw dispatchFailure("SendGrid send failed: " + ex.getResponseBodyAsString(), ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            log.warn("SendGrid dispatch error: {}", ex.getMessage());
            throw new ProviderDispatchException("SendGrid dispatch failed: " + ex.getMessage(), false, ex);
        }
    }
}

