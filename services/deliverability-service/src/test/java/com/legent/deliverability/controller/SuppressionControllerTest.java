package com.legent.deliverability.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        assertThatThrownBy(() -> controller.checkSuppressionsInternal(null, new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void invalidInternalTokenFailsClosedBeforeRepositoryLookup() {
        assertThatThrownBy(() -> controller.checkSuppressionsInternal("wrong-token", new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void validTokenReturnsOnlyTenantWorkspaceScopedCandidateMatches() {
        when(suppressionRepository.findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"),
                eq("workspace-1"),
                eq(List.of("mixed@example.com", "other@example.com"))))
                .thenReturn(List.of(" Mixed@Example.COM "));

        ApiResponse<SuppressionController.SuppressionCheckResponse> response = controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                new SuppressionController.SuppressionCheckRequest(List.of(" Mixed@Example.com ", "other@example.com", "mixed@example.com")));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().checkedCount()).isEqualTo(2);
        assertThat(response.getData().suppressedCount()).isEqualTo(1);
        assertThat(response.getData().suppressedEmails()).containsExactly("mixed@example.com");
    }

    @Test
    void emptyCandidateListDoesNotQueryRepository() {
        ApiResponse<SuppressionController.SuppressionCheckResponse> response = controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                new SuppressionController.SuppressionCheckRequest(List.of(" ", "")));

        assertThat(response.getData().checkedCount()).isZero();
        assertThat(response.getData().suppressedEmails()).isEmpty();
        verify(suppressionRepository, never()).findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void missingWorkspaceContextFailsClosed() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                new SuppressionController.SuppressionCheckRequest(List.of("a@example.com"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void oversizedCandidateListFailsBeforeRepositoryLookup() {
        List<String> emails = IntStream.rangeClosed(0, AppConstants.SEND_BATCH_SIZE)
                .mapToObj(index -> "user" + index + "@example.com")
                .toList();

        assertThatThrownBy(() -> controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                new SuppressionController.SuppressionCheckRequest(emails)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void normalizesAndDeduplicatesCandidatesBeforeRepositoryLookup() {
        when(suppressionRepository.findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        controller.checkSuppressionsInternal(
                INTERNAL_TOKEN,
                new SuppressionController.SuppressionCheckRequest(List.of(" A@Example.com ", "a@example.com", "B@example.com")));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(suppressionRepository).findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                eq("tenant-1"), eq("workspace-1"), captor.capture());
        assertThat(captor.getValue()).containsExactly("a@example.com", "b@example.com");
    }
}
