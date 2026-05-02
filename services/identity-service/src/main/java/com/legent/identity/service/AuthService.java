package com.legent.identity.service;

import com.legent.identity.domain.User;
import com.legent.identity.repository.UserRepository;
import com.legent.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.legent.common.util.IdGenerator;
import com.legent.identity.dto.SignupRequest;
import com.legent.identity.event.IdentityEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final IdentityEventPublisher eventPublisher;

    public String login(String email, String password, String tenantId) {
        if (email == null || password == null || tenantId == null
                || email.isBlank() || password.isBlank() || tenantId.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String normalizedTenantId = tenantId.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByTenantIdAndEmailIgnoreCase(normalizedTenantId, normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return tokenProvider.generateToken(
                user.getId(),
                user.getTenantId(),
                Map.of(
                        "roles", Collections.singletonList(user.getRole()),
                        "email", user.getEmail()));
    }

    @Transactional
    public String signup(SignupRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = request.getCompanyName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        }

        // We use a temporary tenant ID for the user until Foundation provisions it
        // Or we generate the final tenant ID here
        String tenantId = IdGenerator.newId();

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role("ADMIN")
                .isActive(true)
                .build();

        final User savedUser = userRepository.save(java.util.Objects.requireNonNull(user));
        java.util.Objects.requireNonNull(savedUser, "Saved user cannot be null");

        eventPublisher.publishSignup(tenantId, savedUser.getId(), savedUser.getEmail(), request.getCompanyName(), slug);

        log.info("User signed up: email={}, tenantId={}", savedUser.getEmail(), tenantId);

        return tokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getTenantId(),
                Map.of(
                        "roles", Collections.singletonList(savedUser.getRole()),
                        "email", savedUser.getEmail()));
    }

    @Transactional(readOnly = true)
    public List<String> getUserRoles(String tenantId, String userId) {
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .filter(User::isActive)
                .map(User::getRole)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }
}
