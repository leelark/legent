package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AwsSesProviderAdapterTest {

    private final AwsSesProviderAdapter adapter = new AwsSesProviderAdapter(mock(CredentialEncryptionService.class));

    @Test
    void resolveRegion_defaultsToUsEastWhenHostMissing() {
        assertThat(adapter.resolveRegion(provider(null))).isEqualTo(Region.US_EAST_1);
        assertThat(adapter.resolveRegion(provider("  "))).isEqualTo(Region.US_EAST_1);
    }

    @Test
    void resolveRegion_parsesSesApiEndpointHosts() {
        assertThat(adapter.resolveRegion(provider("email.us-west-2.amazonaws.com"))).isEqualTo(Region.US_WEST_2);
        assertThat(adapter.resolveRegion(provider("https://email.eu-central-1.amazonaws.com"))).isEqualTo(Region.EU_CENTRAL_1);
        assertThat(adapter.resolveRegion(provider("ses.ap-southeast-2.amazonaws.com"))).isEqualTo(Region.AP_SOUTHEAST_2);
    }

    @Test
    void resolveRegion_parsesSesSmtpEndpointHosts() {
        assertThat(adapter.resolveRegion(provider("email-smtp.ap-south-1.amazonaws.com"))).isEqualTo(Region.AP_SOUTH_1);
    }

    @Test
    void resolveRegion_rejectsUnsupportedHostInsteadOfSilentlyDefaulting() {
        assertThatThrownBy(() -> adapter.resolveRegion(provider("smtp.example.com")))
                .isInstanceOf(ProviderDispatchException.class)
                .hasMessageContaining("supported SES endpoint");
    }

    private SmtpProvider provider(String host) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId("provider-1");
        provider.setHost(host);
        return provider;
    }
}
