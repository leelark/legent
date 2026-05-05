package com.legent.deliverability.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/deliverability/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionListRepository suppressionRepository;

    @GetMapping
    public ApiResponse<List<SuppressionList>> listSuppressions() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(suppressionRepository.findByTenantId(tenantId));
    }
}
