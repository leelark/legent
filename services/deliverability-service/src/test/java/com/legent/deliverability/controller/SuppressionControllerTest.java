package com.legent.deliverability.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuppressionControllerTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    @Mock private SuppressionListRepository suppressionRepository;

    private SuppressionController controller;

    @BeforeEach
    void setUp() {
        controller = new SuppressionController(suppressionRepository);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void missingInternalTokenFailsClosedBeforeRepositoryLookup() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                null,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void invalidInternalTokenFailsClosedBeforeRepositoryLookup() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                "wrong-token",
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void publicListUsesTenantWorkspaceScopedDefaultFirstPage() {
        SuppressionList suppression = suppression("suppression-1", "user@example.com");
        when(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE)))
                .thenReturn(List.of(suppression));

        ApiResponse<List<SuppressionList>> response = controller.listSuppressions(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly(suppression);
        verify(suppressionRepository).findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE));
    }

    @Test
    void publicListCapsRequestedLimitToMaxFirstPage() {
        when(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)))
                .thenReturn(List.of());

        controller.listSuppressions(AppConstants.MAX_PAGE_SIZE + 1);

        verify(suppressionRepository).findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, AppConstants.MAX_PAGE_SIZE));
    }

    @Test
    void internalListUsesTenantWorkspaceScopedBoundedFirstPage() {
        InternalHeaders headers = headers("campaign-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_LIST_READ);
        SuppressionList suppression = suppression("suppression-1", "user@example.com");
        when(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, 5)))
                .thenReturn(List.of(suppression));

        ApiResponse<List<SuppressionList>> response = controller.listSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                5);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly(suppression);
        verify(suppressionRepository).findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-1",
                PageRequest.of(0, 5));
    }

    @Test
    void internalListInvalidTokenFailsClosedBeforeRepositoryLookup() {
        InternalHeaders headers = headers("campaign-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_LIST_READ);

        assertThatThrownBy(() -> controller.listSuppressionsInternal(
                "wrong-token",
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void internalHistoryInvalidTokenFailsClosedBeforeRepositoryLookup() {
        InternalHeaders headers = headers("campaign-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ);

        assertThatThrownBy(() -> controller.suppressionHistoryInternal(
                "wrong-token",
                headers.service(),
                headers.timestamp(),
                headers.signature()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void validTokenReturnsOnlyTenantWorkspaceScopedCandidateMatches() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);
        when(suppressionRepository.findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(List.of("mixed@example.com", "other@example.com"))))
                .thenReturn(List.of(" Mixed@Example.COM "));

        ApiResponse<SuppressionController.SuppressionCheckResponse> response = controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of(" Mixed@Example.com ", "other@example.com", "mixed@example.com")));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().checkedCount()).isEqualTo(2);
        assertThat(response.getData().suppressedCount()).isEqualTo(1);
        assertThat(response.getData().suppressedEmails()).containsExactly("mixed@example.com");
    }

    @Test
    void emptyCandidateListDoesNotQueryRepository() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);

        ApiResponse<SuppressionController.SuppressionCheckResponse> response = controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of(" ", "")));

        assertThat(response.getData().checkedCount()).isZero();
        assertThat(response.getData().suppressedEmails()).isEmpty();
        verify(suppressionRepository, never()).findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void missingWorkspaceContextFailsClosed() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void oversizedCandidateListFailsBeforeRepositoryLookup() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);
        List<String> emails = IntStream.rangeClosed(0, AppConstants.SEND_BATCH_SIZE)
                .mapToObj(index -> "user" + index + "@example.com")
                .toList();

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(emails)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void normalizesAndDeduplicatesCandidatesBeforeRepositoryLookup() {
        InternalHeaders headers = headers("audience-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);
        when(suppressionRepository.findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature(),
                new SuppressionController.SuppressionCheckRequest(List.of(" A@Example.com ", "a@example.com", "B@example.com")));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(suppressionRepository).findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), captor.capture());
        assertThat(captor.getValue()).containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void suppressionHistoryUsesScopedCountQueriesWithoutLoadingRows() {
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT"))
                .thenReturn(2L);
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE"))
                .thenReturn(3L);
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "UNSUBSCRIBE"))
                .thenReturn(4L);
        when(suppressionRepository.countByTenantIdAndWorkspaceId("tenant-1", "workspace-1"))
                .thenReturn(9L);

        ApiResponse<Map<String, Object>> response = controller.suppressionHistory();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData())
                .containsEntry("total", 9L)
                .containsEntry("complaints", 2L)
                .containsEntry("hardBounces", 3L)
                .containsEntry("unsubscribes", 4L)
                .containsKey("generatedAt");
        verify(suppressionRepository).countByTenantIdAndWorkspaceId("tenant-1", "workspace-1");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "UNSUBSCRIBE");
        verify(suppressionRepository, never()).findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void internalSuppressionHistoryUsesScopedCountQueriesWithoutLoadingRows() {
        InternalHeaders headers = headers("campaign-service", InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ);
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT"))
                .thenReturn(10L);
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE"))
                .thenReturn(50L);
        when(suppressionRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "UNSUBSCRIBE"))
                .thenReturn(4L);
        when(suppressionRepository.countByTenantIdAndWorkspaceId("tenant-1", "workspace-1"))
                .thenReturn(64L);

        ApiResponse<Map<String, Object>> response = controller.suppressionHistoryInternal(
                INTERNAL_TOKEN,
                headers.service(),
                headers.timestamp(),
                headers.signature());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData())
                .containsEntry("total", 64L)
                .containsEntry("complaints", 10L)
                .containsEntry("hardBounces", 50L)
                .containsEntry("unsubscribes", 4L)
                .containsKey("generatedAt");
        verify(suppressionRepository).countByTenantIdAndWorkspaceId("tenant-1", "workspace-1");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE");
        verify(suppressionRepository).countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "UNSUBSCRIBE");
        verify(suppressionRepository, never()).findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                eq("tenant-1"),
                eq("workspace-1"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void suppressionHistoryMissingWorkspaceContextFailsClosedBeforeRepositoryLookup() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(controller::suppressionHistory)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(suppressionRepository);
    }

    private static SuppressionList suppression(String id, String email) {
        SuppressionList suppression = new SuppressionList();
        suppression.setId(id);
        suppression.setTenantId("tenant-1");
        suppression.setWorkspaceId("workspace-1");
        suppression.setEmail(email);
        suppression.setReason("UNSUBSCRIBE");
        return suppression;
    }

    private InternalHeaders headers(String service, String action) {
        Instant timestamp = Instant.now();
        return new InternalHeaders(
                service,
                timestamp.toString(),
                InternalServiceIdentity.sign(INTERNAL_TOKEN, service, "tenant-1", "workspace-1", action, timestamp));
    }

    private record InternalHeaders(String service, String timestamp, String signature) {
    }
}
