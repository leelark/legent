package com.legent.identity.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.LoginRequest;
import com.legent.identity.dto.LoginResponse;
import com.legent.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody LoginRequest request) {
        
        String token = authService.login(request.getEmail(), request.getPassword(), tenantId);
        return ApiResponse.ok(new LoginResponse(token));
    }
}
