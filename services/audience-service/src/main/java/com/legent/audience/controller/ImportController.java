package com.legent.audience.controller;

import com.legent.audience.dto.ImportDto;
import com.legent.audience.service.ImportService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ImportDto.StatusResponse> uploadImport(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestPart("request") @Valid ImportDto.StartRequest request) {
        return ApiResponse.ok(importService.uploadAndStartImport(file, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImportDto.StatusResponse> getStatus(@PathVariable String id) {
        return ApiResponse.ok(importService.getStatus(id));
    }

    @GetMapping
    public PagedResponse<ImportDto.StatusResponse> listImports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ImportDto.StatusResponse> result = importService.listImports(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<String> cancelImport(@PathVariable String id) {
        importService.cancelImport(id);
        return ApiResponse.ok("Import cancelled");
    }
}
