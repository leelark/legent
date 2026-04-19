package com.legent.audience.controller;

import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.service.DataExtensionService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-extensions")
@RequiredArgsConstructor
public class DataExtensionController {

    private final DataExtensionService deService;

    @GetMapping
    public PagedResponse<DataExtensionDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DataExtensionDto.Response> result = deService.list(PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<DataExtensionDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(deService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DataExtensionDto.Response> create(@Valid @RequestBody DataExtensionDto.CreateRequest request) {
        return ApiResponse.ok(deService.create(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        deService.deleteDataExtension(id);
    }

    @PostMapping("/{deId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DataExtensionDto.RecordResponse> addRecord(@PathVariable String deId,
                                                                   @Valid @RequestBody DataExtensionDto.RecordRequest request) {
        return ApiResponse.ok(deService.addRecord(deId, request));
    }

    @GetMapping("/{deId}/records")
    public PagedResponse<DataExtensionDto.RecordResponse> listRecords(@PathVariable String deId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        Page<DataExtensionDto.RecordResponse> result = deService.listRecords(deId, PageRequest.of(page, size));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
}
