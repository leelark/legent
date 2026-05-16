package com.legent.identity.controller;

import com.legent.identity.dto.ExperienceDto;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.UserService;
import com.legent.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final IdentityExperienceService identityExperienceService = mock(IdentityExperienceService.class);
    private final UserController controller = new UserController(userService, identityExperienceService);

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
}
