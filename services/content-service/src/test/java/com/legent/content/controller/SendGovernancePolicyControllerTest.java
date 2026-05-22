package com.legent.content.controller;

import com.legent.content.domain.SendGovernancePolicy;
import com.legent.content.service.SendGovernancePolicyService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SendGovernancePolicyControllerTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-prod-1234567890abcdef";

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void internalPolicyLookupUsesTenantWorkspaceAndInternalToken() {
        SendGovernancePolicyService service = mock(SendGovernancePolicyService.class);
        SendGovernancePolicyController controller = new SendGovernancePolicyController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);

        SendGovernancePolicy policy = new SendGovernancePolicy();
        policy.setId("policy-1");
        policy.setTenantId("tenant-1");
        policy.setWorkspaceId("workspace-1");
        policy.setPolicyKey("promo.default");
        policy.setName("Promo");
        policy.setClassification(SendGovernancePolicy.Classification.COMMERCIAL);
        policy.setUnsubscribePolicy(SendGovernancePolicy.UnsubscribePolicy.REQUIRED);
        policy.setSuppressionRequired(true);
        policy.setSendLogRetentionDays(365);
        policy.setPublicationPolicy("APPROVED_CONTENT_REQUIRED");
        policy.setActive(true);
        when(service.get("tenant-1", "workspace-1", "policy-1")).thenReturn(policy);

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        var response = controller.getInternal("policy-1", "  " + INTERNAL_TOKEN + "  ");

        assertThat(response.getData().getPolicyKey()).isEqualTo("promo.default");
        assertThat(response.getData().getCommercial()).isTrue();
        verify(service).get("tenant-1", "workspace-1", "policy-1");
    }

    @Test
    void internalPolicyLookupRejectsInvalidInternalTokenBeforeServiceAccess() {
        SendGovernancePolicyService service = mock(SendGovernancePolicyService.class);
        SendGovernancePolicyController controller = new SendGovernancePolicyController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        assertThatThrownBy(() -> controller.getInternal("policy-1", "wrong-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(service);
    }
}
