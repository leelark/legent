package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
public class AwsSesProviderAdapter extends ApiProviderAdapterSupport implements ProviderAdapter {

    public AwsSesProviderAdapter(CredentialEncryptionService credentialEncryptionService) {
        super(credentialEncryptionService);
    }

    @Override
    public String getProviderType() {
        return "AWS_SES";
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody, Map<String, String> metadata, SmtpProvider config)
            throws ProviderDispatchException {
        String accessKeyId = config.getUsername();
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new ProviderDispatchException("AWS SES requires access key id in provider username", true);
        }
        String secretAccessKey = resolveSecret(config);
        Region region = resolveRegion(config);
        String fromEmail = resolveFromEmail(metadata, config, to);

        try (SesV2Client client = SesV2Client.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build()) {
            SendEmailRequest.Builder requestBuilder = SendEmailRequest.builder()
                    .fromEmailAddress(fromEmail)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                                    .body(Body.builder().html(Content.builder().data(htmlBody).charset("UTF-8").build()).build())
                                    .build())
                            .build());

            String replyTo = metadata != null ? metadata.get("Reply-To") : null;
            if (replyTo != null && !replyTo.isBlank()) {
                requestBuilder.replyToAddresses(replyTo);
            }

            client.sendEmail(requestBuilder.build());
        } catch (SesV2Exception ex) {
            int statusCode = ex.statusCode();
            String message = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage();
            throw dispatchFailure("AWS SES send failed: " + message, statusCode, ex);
        } catch (Exception ex) {
            log.warn("AWS SES dispatch error: {}", ex.getMessage());
            throw new ProviderDispatchException("AWS SES dispatch failed: " + ex.getMessage(), false, ex);
        }
    }

    private Region resolveRegion(SmtpProvider config) {
        try {
            String host = config.getHost();
            if (host != null && !host.isBlank()) {
                URI uri = host.startsWith("http://") || host.startsWith("https://") ? URI.create(host) : URI.create("https://" + host);
                String hostname = uri.getHost();
                if (hostname != null && hostname.startsWith("email.") && hostname.contains(".amazonaws.com")) {
                    String region = hostname.substring("email.".length(), hostname.indexOf(".amazonaws.com"));
                    if (!region.isBlank()) {
                        return Region.of(region);
                    }
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return Region.US_EAST_1;
    }
}
