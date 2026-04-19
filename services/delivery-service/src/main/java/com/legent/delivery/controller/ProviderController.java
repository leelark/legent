package com.legent.delivery.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.delivery.dto.SmtpProviderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    // Note: In reality this delegates to a ProviderManagementService which uses the repository.
    // For brevity in this module setup, returning dummy values as scaffolding is complete.
    
    @GetMapping
    public ApiResponse<List<SmtpProviderDto.Response>> list() {
        return ApiResponse.ok(List.of());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SmtpProviderDto.Response> create(@RequestBody SmtpProviderDto.CreateRequest request) {
        SmtpProviderDto.Response response = new SmtpProviderDto.Response();
        response.setName(request.getName());
        response.setType(request.getType());
        return ApiResponse.ok(response);
    }
}
