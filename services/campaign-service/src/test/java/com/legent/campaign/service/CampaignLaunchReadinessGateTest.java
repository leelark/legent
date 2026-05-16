package com.legent.campaign.service;

import com.legent.campaign.client.DeliverabilityReadinessClient;
import com.legent.campaign.client.DeliveryReadinessClient;
import com.legent.campaign.client.ReadinessDependencyException;
import com.legent.campaign.domain.Campaign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignLaunchReadinessGateTest {

    @Mock private DeliverabilityReadinessClient deliverabilityClient;
    @Mock private DeliveryReadinessClient deliveryClient;

    private CampaignLaunchReadinessGate gate;

    @BeforeEach
    void setUp() {
        gate = new CampaignLaunchReadinessGate(deliverabilityClient, deliveryClient);
    }

    @Test
    void evaluateBlocksUnverifiedDnsAndUnsafeSuppressionHealth() {
        Campaign campaign = completeCampaign();
        when(deliverabilityClient.authChecks("tenant-1", "workspace-1")).thenReturn(List.of(
                new DeliverabilityReadinessClient.AuthCheck("domain-1", "example.com", "PENDING",
                        true, false, false, true, Instant.now())
        ));
        when(deliverabilityClient.suppressionHealth("tenant-1", "workspace-1")).thenReturn(
                new DeliverabilityReadinessClient.SuppressionHealth(75, 10, 50, 5, Instant.now())
        );
        when(deliveryClient.warmupStatus("tenant-1", "workspace-1")).thenReturn(readyWarmupStatus());
        when(deliveryClient.evaluateProviderCapacity("tenant-1", "workspace-1", "provider-1", "example.com", 0))
                .thenReturn(openCapacity());

        CampaignLaunchReadinessGate.GateResult result = gate.evaluate(campaign);

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).contains(
                "Sending domain example.com must be VERIFIED in deliverability-service before launch.",
                "Sending domain example.com is missing verified DNS authentication: DKIM, DMARC.",
                "Suppression health is blocked: complaint suppressions are at or above 10.",
                "Suppression health is blocked: hard-bounce suppressions are at or above 50."
        );
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.getKey()).isEqualTo("authoritative_launch_gate");
            assertThat(step.getStatus()).isEqualTo("BLOCKED");
        });
    }

    @Test
    void evaluateFailsClosedWhenDeliveryChecksUnavailable() {
        Campaign campaign = completeCampaign();
        when(deliverabilityClient.authChecks("tenant-1", "workspace-1")).thenReturn(List.of(verifiedAuth()));
        when(deliverabilityClient.suppressionHealth("tenant-1", "workspace-1")).thenReturn(
                new DeliverabilityReadinessClient.SuppressionHealth(0, 0, 0, 0, Instant.now())
        );
        when(deliveryClient.warmupStatus("tenant-1", "workspace-1"))
                .thenThrow(new ReadinessDependencyException("delivery-service URL is not configured"));
        when(deliveryClient.evaluateProviderCapacity("tenant-1", "workspace-1", "provider-1", "example.com", 0))
                .thenThrow(new ReadinessDependencyException("service bearer token is not configured for delivery readiness checks"));

        CampaignLaunchReadinessGate.GateResult result = gate.evaluate(campaign);

        assertThat(result.blockers()).contains(
                "Warmup readiness check unavailable: delivery-service URL is not configured.",
                "Provider capacity check unavailable: service bearer token is not configured for delivery readiness checks."
        );
        assertThat(result.blocked()).isTrue();
    }

    @Test
    void evaluatePassesWhenAuthoritativeServicesAreReady() {
        Campaign campaign = completeCampaign();
        when(deliverabilityClient.authChecks("tenant-1", "workspace-1")).thenReturn(List.of(verifiedAuth()));
        when(deliverabilityClient.suppressionHealth("tenant-1", "workspace-1")).thenReturn(
                new DeliverabilityReadinessClient.SuppressionHealth(0, 0, 0, 0, Instant.now())
        );
        when(deliveryClient.warmupStatus("tenant-1", "workspace-1")).thenReturn(readyWarmupStatus());
        when(deliveryClient.evaluateProviderCapacity("tenant-1", "workspace-1", "provider-1", "example.com", 0))
                .thenReturn(openCapacity());

        CampaignLaunchReadinessGate.GateResult result = gate.evaluate(campaign);

        assertThat(result.blocked()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.getStatus()).isEqualTo("PASS");
            assertThat(step.getScore()).isEqualTo(20);
        });
    }

    @Test
    void evaluateBlocksMissingProviderBeforeDeliveryServiceChecks() {
        Campaign campaign = completeCampaign();
        campaign.setProviderId(null);
        when(deliverabilityClient.authChecks("tenant-1", "workspace-1")).thenReturn(List.of(verifiedAuth()));
        when(deliverabilityClient.suppressionHealth("tenant-1", "workspace-1")).thenReturn(
                new DeliverabilityReadinessClient.SuppressionHealth(0, 0, 0, 0, Instant.now())
        );

        CampaignLaunchReadinessGate.GateResult result = gate.evaluate(campaign);

        assertThat(result.blockers()).contains("Delivery provider is required before launch readiness can verify warmup and capacity.");
        assertThat(result.blocked()).isTrue();
    }

    private DeliverabilityReadinessClient.AuthCheck verifiedAuth() {
        return new DeliverabilityReadinessClient.AuthCheck("domain-1", "example.com", "VERIFIED",
                true, true, true, true, Instant.now());
    }

    private DeliveryReadinessClient.WarmupStatus readyWarmupStatus() {
        return new DeliveryReadinessClient.WarmupStatus(
                1,
                1,
                0,
                0,
                "RAMPING",
                100,
                null,
                Instant.now().plusSeconds(3600),
                List.of(new DeliveryReadinessClient.WarmupStateSnapshot(
                        "example.com",
                        "provider-1",
                        "RAMPING",
                        100,
                        500,
                        10,
                        100,
                        0.01,
                        0.0,
                        null,
                        Instant.now().plusSeconds(3600)
                ))
        );
    }

    private DeliveryReadinessClient.ProviderCapacityDecision openCapacity() {
        return new DeliveryReadinessClient.ProviderCapacityDecision(
                "provider-1",
                "OPEN",
                60,
                1,
                null,
                "provider capacity open",
                Instant.now()
        );
    }

    private Campaign completeCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Ready campaign");
        campaign.setSubject("Launch subject");
        campaign.setContentId("template-1");
        campaign.setSenderEmail("marketing@example.com");
        campaign.setSendingDomain("example.com");
        campaign.setProviderId("provider-1");
        campaign.setApprovalRequired(false);
        campaign.addAudience("LIST", "list-1");
        return campaign;
    }
}
