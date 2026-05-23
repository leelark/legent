package com.legent.identity.controller;

import com.legent.common.constant.AppConstants;
import com.legent.identity.domain.User;
import com.legent.identity.dto.ExperienceDto;
import com.legent.identity.dto.UserDto;
import com.legent.identity.repository.UserRepository;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.UserService;
import com.legent.security.TenantContext;
import com.legent.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final IdentityExperienceService identityExperienceService = mock(IdentityExperienceService.class);
    private final UserController controller = new UserController(userService, identityExperienceService);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void listUsers_passesLimitToServiceAndKeepsListResponseShape() {
        UserDto.Response user = UserDto.Response.builder()
                .id("user-1")
                .tenantId("tenant-1")
                .email("user@example.com")
                .roles(List.of("ADMIN"))
                .build();
        when(userService.listUsers(7)).thenReturn(List.of(user));

        var response = controller.listUsers(7);

        assertEquals(List.of(user), response.getData());
        verify(userService).listUsers(7);
    }

    @Test
    void listUsers_whenLimitMissing_usesDefaultBoundedTenantRepositoryRead() {
        UserRepository userRepository = mock(UserRepository.class);
        UserService service = new UserService(userRepository, mock(PasswordEncoder.class));
        TenantContext.setTenantId("tenant-1");
        User user = user("user-1", "user@example.com");
        when(userRepository.findAll(any(Example.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        var response = new UserController(service, identityExperienceService).listUsers(null);

        assertEquals("user-1", response.getData().getFirst().getId());
        ArgumentCaptor<Example> exampleCaptor = ArgumentCaptor.forClass(Example.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(exampleCaptor.capture(), pageableCaptor.capture());
        assertEquals("tenant-1", ((User) exampleCaptor.getValue().getProbe()).getTenantId());
        assertEquals(AppConstants.DEFAULT_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    void listUsers_whenLimitExceedsMax_clampsRepositoryRead() {
        UserRepository userRepository = mock(UserRepository.class);
        UserService service = new UserService(userRepository, mock(PasswordEncoder.class));
        TenantContext.setTenantId("tenant-1");
        when(userRepository.findAll(any(Example.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        new UserController(service, identityExperienceService)
                .listUsers(AppConstants.MAX_PAGE_SIZE + 1_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Example.class), pageableCaptor.capture());
        assertEquals(AppConstants.MAX_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getPreferences_acceptsSharedSecurityPrincipal() {
        ExperienceDto.UserPreferenceResponse preferences = new ExperienceDto.UserPreferenceResponse();
        preferences.setTenantId("tenant-1");
        preferences.setUserId("user-1");
        when(identityExperienceService.getPreferences("user-1", "tenant-1")).thenReturn(preferences);

        var response = controller.getPreferences(authentication());

        assertEquals("user-1", response.getData().getUserId());
        assertEquals("tenant-1", response.getData().getTenantId());
        verify(identityExperienceService).getPreferences("user-1", "tenant-1");
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal("user-1", "tenant-1", Set.of("ADMIN")),
                "token");
    }

    private User user(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setTenantId("tenant-1");
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole("ADMIN");
        user.setActive(true);
        return user;
    }
}
