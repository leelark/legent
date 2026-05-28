package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.content.domain.SendGovernancePolicy;
import com.legent.content.service.SendGovernancePolicyService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.Instant;

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
    void listClampsInvalidPageAndSizeBeforeServiceCall() {
        SendGovernancePolicyService service = mock(SendGovernancePolicyService.class);
        SendGovernancePolicyController controller = new SendGovernancePolicyController(service);
        PageRequest expectedPage = PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE);
        when(service.list("tenant-1", "workspace-1", expectedPage))
                .thenReturn(new PageImpl<>(List.of(), expectedPage, 42));

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        var response = controller.list(-5, 0);

        assertThat(response.getPagination().getPage()).isZero();
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(42);
        verify(service).list("tenant-1", "workspace-1", expectedPage);
    }

    @Test
    void listClampsExcessiveSizeBeforeServiceCall() {
        SendGovernancePolicyService service = mock(SendGovernancePolicyService.class);
        SendGovernancePolicyController controller = new SendGovernancePolicyController(service);
        PageRequest expectedPage = PageRequest.of(2, AppConstants.MAX_PAGE_SIZE);
        when(service.list("tenant-1", "workspace-1", expectedPage))
                .thenReturn(new PageImpl<>(List.of(), expectedPage, 0));

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        var response = controller.list(2, AppConstants.MAX_PAGE_SIZE + 500);

        assertThat(response.getPagination().getPage()).isEqualTo(2);
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.MAX_PAGE_SIZE);
        verify(service).list("tenant-1", "workspace-1", expectedPage);
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

        Instant timestamp = Instant.now();
        var response = controller.getInternal(
                "policy-1",
                "  " + INTERNAL_TOKEN + "  ",
                "campaign-service",
                timestamp.toString(),
                signature(
                        "campaign-service",
                        "tenant-1",
                        "workspace-1",
                        InternalServiceIdentity.scopedAction(
                                InternalServiceIdentity.ACTION_CONTENT_SEND_GOVERNANCE_POLICY_READ,
                                "policy-1"),
                        timestamp));

        assertThat(response.getData().getPolicyKey()).isEqualTo("promo.default");
        assertThat(response.getData().getCommercial()).isTrue();
        assertThat(response.getData().getVersion()).isZero();
        verify(service).get("tenant-1", "workspace-1", "policy-1");
    }

    @Test
    void internalPolicyLookupRejectsInvalidInternalTokenBeforeServiceAccess() {
        SendGovernancePolicyService service = mock(SendGovernancePolicyService.class);
        SendGovernancePolicyController controller = new SendGovernancePolicyController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        Instant timestamp = Instant.now();
        assertThatThrownBy(() -> controller.getInternal(
                "policy-1",
                "wrong-token",
                "campaign-service",
                timestamp.toString(),
                signature(
                        "campaign-service",
                        "tenant-1",
                        "workspace-1",
                        InternalServiceIdentity.scopedAction(
                                InternalServiceIdentity.ACTION_CONTENT_SEND_GOVERNANCE_POLICY_READ,
                                "policy-1"),
                        timestamp)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(service);
    }

    private String signature(String serviceName,
                             String tenantId,
                             String workspaceId,
                             String action,
                             Instant timestamp) {
        return InternalServiceIdentity.sign(INTERNAL_TOKEN, serviceName, tenantId, workspaceId, action, timestamp);
    }
}
