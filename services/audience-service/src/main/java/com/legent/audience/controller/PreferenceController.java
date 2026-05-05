package com.legent.audience.controller;

import com.legent.audience.dto.PreferenceDto;
import com.legent.audience.service.PreferenceService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping("/{subscriberId}")
    public ApiResponse<PreferenceDto.Response> get(@PathVariable String subscriberId) {
        return ApiResponse.ok(preferenceService.get(subscriberId));
    }

    @PutMapping("/{subscriberId}")
    public ApiResponse<PreferenceDto.Response> update(@PathVariable String subscriberId,
                                                      @Valid @RequestBody PreferenceDto.UpdateRequest request) {
        return ApiResponse.ok(preferenceService.update(subscriberId, request));
    }

    @PostMapping("/{subscriberId}/pause")
    public ApiResponse<PreferenceDto.Response> pause(@PathVariable String subscriberId,
                                                     @Valid @RequestBody PreferenceDto.PauseRequest request) {
        return ApiResponse.ok(preferenceService.pause(subscriberId, request));
    }

    @PostMapping("/{subscriberId}/unsubscribe")
    public ApiResponse<PreferenceDto.Response> unsubscribe(@PathVariable String subscriberId,
                                                           @Valid @RequestBody PreferenceDto.UnsubscribeRequest request) {
        return ApiResponse.ok(preferenceService.unsubscribe(subscriberId, request));
    }

    @PostMapping("/{subscriberId}/resubscribe")
    public ApiResponse<PreferenceDto.Response> resubscribe(@PathVariable String subscriberId) {
        return ApiResponse.ok(preferenceService.resubscribe(subscriberId));
    }
}
