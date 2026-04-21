package com.legent.identity.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.UserDto;
import com.legent.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
}
