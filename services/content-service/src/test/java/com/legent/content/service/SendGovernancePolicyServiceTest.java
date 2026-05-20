package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.SendGovernancePolicy;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.SendGovernancePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendGovernancePolicyServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";

    @Mock
    private SendGovernancePolicyRepository repository;

    private SendGovernancePolicyService service;

    @BeforeEach
    void setUp() {
        service = new SendGovernancePolicyService(repository);
    }

    @Test
    void createStoresTenantWorkspaceAndNormalizesControls() {
        EmailStudioDto.SendGovernancePolicyRequest request = validRequest();
        request.setPolicyKey("Promo.Monthly");
        request.setSendingDomain("Example.COM.");

        when(repository.existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "Promo.Monthly")).thenReturn(false);
        when(repository.save(any(SendGovernancePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendGovernancePolicy policy = service.create(TENANT_ID, WORKSPACE_ID, request);

        assertThat(policy.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(policy.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(policy.getPolicyKey()).isEqualTo("Promo.Monthly");
        assertThat(policy.getSendingDomain()).isEqualTo("example.com");
        assertThat(policy.getClassification()).isEqualTo(SendGovernancePolicy.Classification.COMMERCIAL);
        assertThat(policy.getUnsubscribePolicy()).isEqualTo(SendGovernancePolicy.UnsubscribePolicy.REQUIRED);
        assertThat(policy.getSuppressionRequired()).isTrue();
        verify(repository).save(policy);
    }

    @Test
    void createCommercialPolicyRequiresSuppressionAndUnsubscribe() {
        EmailStudioDto.SendGovernancePolicyRequest request = validRequest();
        request.setSuppressionRequired(false);

        assertThatThrownBy(() -> service.create(TENANT_ID, WORKSPACE_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Commercial send policies must require suppression checks");

        request.setSuppressionRequired(true);
        request.setUnsubscribePolicy("OPTIONAL");

        assertThatThrownBy(() -> service.create(TENANT_ID, WORKSPACE_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Commercial send policies must require unsubscribe handling");
    }

    @Test
    void duplicatePolicyKeyIsScopedToWorkspace() {
        EmailStudioDto.SendGovernancePolicyRequest request = validRequest();
        when(repository.existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "promo.default")).thenReturn(true);

        assertThatThrownBy(() -> service.create(TENANT_ID, WORKSPACE_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Send governance policy already exists");
    }

    @Test
    void getRequiresTenantWorkspaceScope() {
        when(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("policy-1", TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TENANT_ID, WORKSPACE_ID, "policy-1"))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> service.list(TENANT_ID, " ", PageRequest.of(0, 10)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("workspaceId");
    }

    @Test
    void updateRejectsDuplicatePolicyKeyAndRetentionOutOfRange() {
        SendGovernancePolicy existing = persistedPolicy();
        EmailStudioDto.SendGovernancePolicyRequest request = validRequest();
        request.setPolicyKey("journey.receipts");
        request.setClassification("TRANSACTIONAL");
        request.setUnsubscribePolicy("NOT_APPLICABLE");
        request.setSendLogRetentionDays(0);

        when(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("policy-1", TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(TENANT_ID, WORKSPACE_ID, "policy-1", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Retention must be between 1 and 2555 days");

        request.setSendLogRetentionDays(90);
        existing.setPolicyKey("promo.default");
        when(repository.existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "journey.receipts")).thenReturn(true);

        assertThatThrownBy(() -> service.update(TENANT_ID, WORKSPACE_ID, "policy-1", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("journey.receipts");
    }

    private EmailStudioDto.SendGovernancePolicyRequest validRequest() {
        EmailStudioDto.SendGovernancePolicyRequest request = new EmailStudioDto.SendGovernancePolicyRequest();
        request.setPolicyKey("promo.default");
        request.setName("Default promotional policy");
        request.setClassification("COMMERCIAL");
        request.setSenderProfileId("sender:marketing");
        request.setDeliveryProfileId("delivery:primary");
        request.setSendingDomain("example.com");
        request.setProviderId("provider-1");
        request.setUnsubscribePolicy("REQUIRED");
        request.setSuppressionRequired(true);
        request.setConsentRequired(false);
        request.setTrackingAllowed(true);
        request.setSendLogRetentionDays(365);
        request.setPublicationPolicy("APPROVED_CONTENT_REQUIRED");
        request.setActive(true);
        return request;
    }

    private SendGovernancePolicy persistedPolicy() {
        SendGovernancePolicy policy = new SendGovernancePolicy();
        policy.setId("policy-1");
        policy.setTenantId(TENANT_ID);
        policy.setWorkspaceId(WORKSPACE_ID);
        policy.setPolicyKey("promo.default");
        policy.setName("Default promotional policy");
        policy.setClassification(SendGovernancePolicy.Classification.COMMERCIAL);
        policy.setUnsubscribePolicy(SendGovernancePolicy.UnsubscribePolicy.REQUIRED);
        policy.setSuppressionRequired(true);
        policy.setSendLogRetentionDays(365);
        policy.setPublicationPolicy("APPROVED_CONTENT_REQUIRED");
        policy.setActive(true);
        return policy;
    }
}
