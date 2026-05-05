package com.legent.identity.service;

import com.legent.identity.domain.User;
import com.legent.identity.repository.TenantRepository;
import com.legent.identity.repository.UserRepository;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private IdentityEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_whenCredentialsValid_returnsToken() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(tokenProvider.generateToken(eq("user-1"), eq("tenant-1"), anyMap())).thenReturn("jwt-token");

        String token = authService.login(" User@Example.com ", "secret", "tenant-1");

        assertEquals("jwt-token", token);
        verify(userRepository).findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com");
    }

    @Test
    void login_whenUserInactive_throwsBadCredentials() {
        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setActive(false);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> authService.login("user@example.com", "secret", "tenant-1"));

        verifyNoInteractions(passwordEncoder, tokenProvider);
    }

    @Test
    void login_whenPasswordInvalid_throwsBadCredentials() {
        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setActive(true);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-secret", "encoded")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login("user@example.com", "bad-secret", "tenant-1"));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void getUserRoles_whenUserExistsAndActive_returnsRole() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("tenant-1");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1"))
                .thenReturn(Optional.of(user));

        var roles = authService.getUserRoles("tenant-1", "user-1");

        assertEquals(1, roles.size());
        assertEquals("ADMIN", roles.get(0));
    }

    @Test
    void getUserRoles_whenUserMissing_returnsEmptyList() {
        when(userRepository.findByTenantIdAndId("tenant-1", "missing"))
                .thenReturn(Optional.empty());

        var roles = authService.getUserRoles("tenant-1", "missing");

        assertTrue(roles.isEmpty());
    }
}
