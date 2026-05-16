package com.legent.audience.controller;

import java.util.List;

import com.legent.audience.service.SendEligibilityService;
import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audience")
@RequiredArgsConstructor
public class SendEligibilityController {

    private final SendEligibilityService sendEligibilityService;

    @PostMapping("/send-eligibility")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:read', principal.roles)")
    public ApiResponse<List<SendEligibilityService.EligibilityResult>> check(@RequestBody EligibilityRequest request) {
        return ApiResponse.ok(sendEligibilityService.check(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                request == null ? List.of() : request.emails(),
                request == null ? List.of() : request.subscriberIds()));
    }

    public record EligibilityRequest(List<String> emails, List<String> subscriberIds) {
    }
}
