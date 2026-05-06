package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class MailgunProviderAdapter extends ApiProviderAdapterSupport implements ProviderAdapter {

    private final WebClient webClient;

    public MailgunProviderAdapter(CredentialEncryptionService credentialEncryptionService) {
        super(credentialEncryptionService);
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String getProviderType() {
        return "MAILGUN";
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody, Map<String, String> metadata, SmtpProvider config)
            throws ProviderDispatchException {
        String apiKey = resolveSecret(config);
        URI baseUri = resolveBaseUri(config, "https://api.mailgun.net");
        String sendingDomain = config.getUsername();
        if (sendingDomain == null || sendingDomain.isBlank()) {
            throw new ProviderDispatchException("Mailgun provider username must contain sending domain", true);
        }

        String fromEmail = resolveFromEmail(metadata, config, to);
        String fromName = metadata != null ? metadata.get("From-Name") : null;
        String from = fromName != null && !fromName.isBlank()
                ? fromName + " <" + fromEmail + ">"
                : fromEmail;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("from", from);
        form.add("to", to);
        form.add("subject", subject);
        form.add("html", htmlBody);

        String auth = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes(StandardCharsets.UTF_8));

        try {
            webClient.post()
                    .uri(baseUri.resolve("/v3/" + sendingDomain + "/messages"))
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> response.createException()
                                            .map(ex -> new RuntimeException("Mailgun send failed " + response.statusCode() + " " + body, ex))))
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(15))
                    .block();
        } catch (WebClientResponseException ex) {
            throw dispatchFailure("Mailgun send failed: " + ex.getResponseBodyAsString(), ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            log.warn("Mailgun dispatch error: {}", ex.getMessage());
            throw new ProviderDispatchException("Mailgun dispatch failed: " + ex.getMessage(), false, ex);
        }
    }
}

