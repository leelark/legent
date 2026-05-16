package com.legent.identity.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.ExperienceDto;
import com.legent.identity.dto.UserDto;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.UserService;
import com.legent.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final IdentityExperienceService identityExperienceService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('user:read', principal.roles)")
    public ApiResponse<List<UserDto.Response>> listUsers() {
        return ApiResponse.ok(userService.listUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('user:read', principal.roles)")
    public ApiResponse<UserDto.Response> getUser(@PathVariable String id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('user:write', principal.roles)")
    public ApiResponse<UserDto.Response> createUser(@Valid @RequestBody UserDto.Request request) {
        return ApiResponse.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('user:write', principal.roles)")
    public ApiResponse<UserDto.Response> updateUser(@PathVariable String id, @Valid @RequestBody UserDto.Request request) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('user:delete', principal.roles)")
    public void deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
    }

    @GetMapping("/preferences")
    public ApiResponse<ExperienceDto.UserPreferenceResponse> getPreferences(Authentication authentication) {
        UserPrincipal principal = requirePrincipal(authentication);
        return ApiResponse.ok(identityExperienceService.getPreferences(principal.getUserId(), principal.getTenantId()));
    }

    @PutMapping("/preferences")
    public ApiResponse<ExperienceDto.UserPreferenceResponse> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody ExperienceDto.UserPreferenceRequest request) {
        UserPrincipal principal = requirePrincipal(authentication);
        return ApiResponse.ok(identityExperienceService.updatePreferences(principal.getUserId(), principal.getTenantId(), request));
    }

    private UserPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalArgumentException("Invalid user session");
        }
        return principal;
    }
}
