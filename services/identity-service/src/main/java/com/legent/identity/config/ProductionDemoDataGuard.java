package com.legent.identity.config;

import com.legent.identity.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionDemoDataGuard {

    private final UserRepository userRepository;

    @Value("${legent.security.legacy-demo-admin-email:}")
    private String legacyDemoAdminEmail;

    @Value("${legent.security.legacy-demo-admin-password-hash:}")
    private String legacyDemoAdminPasswordHash;

    @PostConstruct
    public void rejectDemoAdmin() {
        if (legacyDemoAdminEmail == null || legacyDemoAdminEmail.isBlank()
                || legacyDemoAdminPasswordHash == null || legacyDemoAdminPasswordHash.isBlank()) {
            return;
        }
        userRepository.findFirstByEmailIgnoreCase(legacyDemoAdminEmail)
                .filter(user -> user.isActive() && legacyDemoAdminPasswordHash.equals(user.getPasswordHash()))
                .ifPresent(user -> {
                    throw new IllegalStateException("Production cannot start with a configured legacy demo admin credential.");
                });
    }
}
