package com.legent.identity.config;

import com.legent.identity.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionDemoDataGuard {

    private static final String DEMO_EMAIL = "admin@legent.com";
    private static final String DEMO_PASSWORD_HASH = "$2b$10$uljUCrJIHC0EFF8t4ZMkUeClESdARKImtgPJKniwGmQ1Yj2lwDLee";

    private final UserRepository userRepository;

    @PostConstruct
    public void rejectDemoAdmin() {
        userRepository.findFirstByEmailIgnoreCase(DEMO_EMAIL)
                .filter(user -> user.isActive() && DEMO_PASSWORD_HASH.equals(user.getPasswordHash()))
                .ifPresent(user -> {
                    throw new IllegalStateException("Production cannot start with the seeded demo admin credential. Disable or rotate admin@legent.com before enabling prod profile.");
                });
    }
}
