package com.legent.identity.service;

import com.legent.identity.domain.User;
import com.legent.identity.repository.UserRepository;
import com.legent.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

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
                        "email", user.getEmail()
                )
        );
    }
}
