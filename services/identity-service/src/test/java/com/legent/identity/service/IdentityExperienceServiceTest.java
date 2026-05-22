package com.legent.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.PasswordResetToken;
import com.legent.identity.domain.User;
import com.legent.identity.dto.ExperienceDto;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.OnboardingStateRepository;
import com.legent.identity.repository.PasswordResetTokenRepository;
import com.legent.identity.repository.UserPreferenceRepository;
import com.legent.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityExperienceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountMembershipRepository accountMembershipRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private OnboardingStateRepository onboardingStateRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IdentityEventPublisher identityEventPublisher;

    private IdentityExperienceService service;

    @BeforeEach
    void setUp() {
        service = new IdentityExperienceService(
                userRepository,
                accountMembershipRepository,
                passwordResetTokenRepository,
                onboardingStateRepository,
                userPreferenceRepository,
                refreshTokenService,
                passwordEncoder,
                new ObjectMapper(),
                identityEventPublisher);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://app.legent.test");
    }

    @Test
    void requestPasswordReset_withoutTenantSkipsAmbiguousDuplicateEmail() {
        when(userRepository.findAllByEmailIgnoreCase("user@example.com"))
                .thenReturn(List.of(user("user-1", "tenant-1"), user("user-2", "tenant-2")));

        service.requestPasswordReset(" USER@example.com ");

        verifyNoInteractions(passwordResetTokenRepository, identityEventPublisher);
        verify(accountMembershipRepository, never()).findAllByUserIdAndTenantId(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_withTenantAndWorkspacePublishesForRealMembership() {
        User user = user("user-2", "tenant-2");
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-2", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(accountMembershipRepository.findAllByUserIdAndTenantId("user-2", "tenant-2"))
                .thenReturn(List.of(membership("workspace-2", true)));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> {
            PasswordResetToken token = invocation.getArgument(0);
            token.setId("reset-1");
            return token;
        });

        ExperienceDto.ForgotPasswordRequest request = new ExperienceDto.ForgotPasswordRequest();
        request.setEmail("user@example.com");
        request.setTenantId("tenant-2");
        request.setWorkspaceId("workspace-2");

        service.requestPasswordReset(request);

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(identityEventPublisher).publishPasswordResetEmail(
                eq("tenant-2"),
                eq("workspace-2"),
                eq("user-2"),
                eq("user@example.com"),
                org.mockito.ArgumentMatchers.contains("/reset-password?token="),
                eq("reset-1"));
    }

    @Test
    void requestPasswordReset_skipsWhenRequestedWorkspaceIsNotAnActiveMembership() {
        User user = user("user-1", "tenant-1");
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(accountMembershipRepository.findAllByUserIdAndTenantId("user-1", "tenant-1"))
                .thenReturn(List.of(membership("workspace-1", true)));

        service.requestPasswordReset("user@example.com", "tenant-1", "workspace-2");

        verifyNoInteractions(passwordResetTokenRepository, identityEventPublisher);
    }

    @Test
    void requestPasswordReset_skipsWhenNoActiveWorkspaceExists() {
        User user = user("user-1", "tenant-1");
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        AccountMembership inactive = membership("workspace-1", true);
        inactive.setStatus("SUSPENDED");
        when(accountMembershipRepository.findAllByUserIdAndTenantId("user-1", "tenant-1"))
                .thenReturn(List.of(inactive));

        service.requestPasswordReset("user@example.com", "tenant-1", null);

        verifyNoInteractions(passwordResetTokenRepository, identityEventPublisher);
    }

    private User user(String id, String tenantId) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setEmail("user@example.com");
        user.setActive(true);
        return user;
    }

    private AccountMembership membership(String workspaceId, boolean defaultMembership) {
        AccountMembership membership = new AccountMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setStatus("ACTIVE");
        membership.setDefaultMembership(defaultMembership);
        return membership;
    }
}
