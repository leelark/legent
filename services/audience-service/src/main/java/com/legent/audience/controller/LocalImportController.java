package com.legent.audience.controller;

import com.legent.audience.dto.ImportDto;
import com.legent.audience.service.ImportService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class LocalImportController {

    private final ImportService importService;

    @PostMapping("/mock")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<ImportDto.StatusResponse> startImport(@Valid @RequestBody ImportDto.StartRequest request) {
        return ApiResponse.ok(importService.startImport(request));
    }
}
