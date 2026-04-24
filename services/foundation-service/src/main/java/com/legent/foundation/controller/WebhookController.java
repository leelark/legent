package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.dto.WebhookDto;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WebhookController {
    private final WebhookIntegrationRepository repo;

    @GetMapping
    public ApiResponse<List<WebhookDto.Response>> list() {
        return ApiResponse.ok(
                repo.findAll().stream().map(this::toResponse).collect(Collectors.toList())
        );
    }

    @PostMapping
    public ApiResponse<WebhookDto.Response> save(@Valid @RequestBody WebhookDto.UpsertRequest request) {
        WebhookIntegration wh = new WebhookIntegration();
        wh.setName(request.getName());
        wh.setUrl(request.getUrl());
        wh.setEventType(request.getEventType());
        return ApiResponse.ok(toResponse(repo.save(wh)));
    }

    private WebhookDto.Response toResponse(WebhookIntegration webhookIntegration) {
        return WebhookDto.Response.builder()
                .id(webhookIntegration.getId())
                .name(webhookIntegration.getName())
                .url(webhookIntegration.getUrl())
                .eventType(webhookIntegration.getEventType())
                .build();
    }
}
