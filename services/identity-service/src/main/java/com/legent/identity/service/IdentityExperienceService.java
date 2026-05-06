package com.legent.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.OnboardingState;
import com.legent.identity.domain.PasswordResetToken;
import com.legent.identity.domain.User;
import com.legent.identity.domain.UserPreference;
import com.legent.identity.dto.ExperienceDto;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.OnboardingStateRepository;
import com.legent.identity.repository.PasswordResetTokenRepository;
import com.legent.identity.repository.UserPreferenceRepository;
import com.legent.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityExperienceService {

    private final UserRepository userRepository;
    private final AccountMembershipRepository accountMembershipRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OnboardingStateRepository onboardingStateRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final IdentityEventPublisher identityEventPublisher;

    @Value("${legent.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        Optional<User> userOpt = userRepository.findFirstByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return;
        }

        String workspaceId = resolveWorkspaceId(user.getId(), user.getTenantId());
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);
        String resetUrl = frontendBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + rawToken;

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTenantId(user.getTenantId());
        resetToken.setUserId(user.getId());
        resetToken.setEmail(user.getEmail());
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(30 * 60));
        resetToken.setMetadata("{\"source\":\"frontend\"}");
        passwordResetTokenRepository.save(resetToken);

        identityEventPublisher.publishPasswordResetEmail(
                user.getTenantId(),
                workspaceId,
                user.getId(),
                user.getEmail(),
                resetUrl,
                resetToken.getId());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Token and new password are required");
        }

        String tokenHash = hashToken(token);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (resetToken.getUsedAt() != null) {
            throw new IllegalArgumentException("Reset token already used");
        }
        if (resetToken.getExpiresAt() == null || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset token expired");
        }

        User user = userRepository.findByTenantIdAndId(resetToken.getTenantId(), resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for reset token"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        refreshTokenService.revokeAllUserTokens(user.getId(), user.getTenantId());
    }

    @Transactional
    public Map<String, Object> startOnboarding(String userId, String tenantId, ExperienceDto.OnboardingStartRequest request) {
        OnboardingState state = onboardingStateRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(OnboardingState::new);
        state.setTenantId(tenantId);
        state.setUserId(userId);
        state.setWorkspaceId(blankToNull(request.getWorkspaceId()));
        state.setStatus("STARTED");
        state.setStepKey(blankToNull(request.getStepKey()) == null ? "workspace" : request.getStepKey());
        state.setStartedAt(state.getStartedAt() == null ? Instant.now() : state.getStartedAt());
        state.setPayload(toJson(request.getPayload() == null ? Map.of() : request.getPayload()));
        onboardingStateRepository.save(state);
        return onboardingResponse(state);
    }

    @Transactional
    public Map<String, Object> completeOnboarding(String userId, String tenantId, ExperienceDto.OnboardingCompleteRequest request) {
        OnboardingState state = onboardingStateRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(OnboardingState::new);
        state.setTenantId(tenantId);
        state.setUserId(userId);
        state.setWorkspaceId(blankToNull(request.getWorkspaceId()));
        state.setStatus("COMPLETED");
        state.setStepKey("completed");
        state.setPayload(toJson(request.getPayload() == null ? Map.of() : request.getPayload()));
        state.setStartedAt(state.getStartedAt() == null ? Instant.now() : state.getStartedAt());
        state.setCompletedAt(Instant.now());
        onboardingStateRepository.save(state);
        return onboardingResponse(state);
    }

    @Transactional(readOnly = true)
    public ExperienceDto.UserPreferenceResponse getPreferences(String userId, String tenantId) {
        UserPreference preference = userPreferenceRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> buildDefaultPreference(userId, tenantId));
        return toResponse(preference);
    }

    @Transactional
    public ExperienceDto.UserPreferenceResponse updatePreferences(String userId, String tenantId, ExperienceDto.UserPreferenceRequest request) {
        UserPreference preference = userPreferenceRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> buildDefaultPreference(userId, tenantId));

        if (request.getUiMode() != null && !request.getUiMode().isBlank()) {
            String uiMode = request.getUiMode().trim().toUpperCase(Locale.ROOT);
            preference.setUiMode("ADVANCED".equals(uiMode) ? "ADVANCED" : "BASIC");
        }
        if (request.getTheme() != null && !request.getTheme().isBlank()) {
            String theme = request.getTheme().trim().toLowerCase(Locale.ROOT);
            preference.setTheme("dark".equals(theme) ? "dark" : "light");
        }
        if (request.getDensity() != null && !request.getDensity().isBlank()) {
            preference.setDensity(request.getDensity().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getSidebarCollapsed() != null) {
            preference.setSidebarCollapsed(request.getSidebarCollapsed());
        }
        if (request.getMetadata() != null) {
            preference.setMetadata(toJson(request.getMetadata()));
        }

        userPreferenceRepository.save(preference);
        return toResponse(preference);
    }

    private ExperienceDto.UserPreferenceResponse toResponse(UserPreference preference) {
        ExperienceDto.UserPreferenceResponse response = new ExperienceDto.UserPreferenceResponse();
        response.setTenantId(preference.getTenantId());
        response.setUserId(preference.getUserId());
        response.setUiMode(preference.getUiMode());
        response.setTheme(preference.getTheme());
        response.setDensity(preference.getDensity());
        response.setSidebarCollapsed(preference.isSidebarCollapsed());
        response.setMetadata(parseJson(preference.getMetadata()));
        return response;
    }

    private UserPreference buildDefaultPreference(String userId, String tenantId) {
        UserPreference preference = new UserPreference();
        preference.setTenantId(tenantId);
        preference.setUserId(userId);
        preference.setUiMode("BASIC");
        preference.setTheme("light");
        preference.setDensity("comfortable");
        preference.setSidebarCollapsed(false);
        preference.setMetadata("{}");
        return preference;
    }

    private String resolveWorkspaceId(String userId, String tenantId) {
        List<AccountMembership> memberships = accountMembershipRepository.findAllByUserIdAndTenantId(userId, tenantId);
        return memberships.stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .sorted(Comparator.comparing(AccountMembership::isDefaultMembership).reversed())
                .map(AccountMembership::getWorkspaceId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("workspace-default");
    }

    private Map<String, Object> onboardingResponse(OnboardingState state) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", state.getTenantId());
        response.put("userId", state.getUserId());
        response.put("workspaceId", state.getWorkspaceId());
        response.put("status", state.getStatus());
        response.put("stepKey", state.getStepKey());
        response.put("payload", parseJson(state.getPayload()));
        response.put("startedAt", state.getStartedAt());
        response.put("completedAt", state.getCompletedAt());
        return response;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload", e);
        }
    }

    private Map<String, Object> parseJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse preference json: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
