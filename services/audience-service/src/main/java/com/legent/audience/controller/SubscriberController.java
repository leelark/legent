package com.legent.audience.controller;

import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.service.SubscriberService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscribers")
@RequiredArgsConstructor
public class SubscriberController {

    private final SubscriberService subscriberService;

    @GetMapping
    public PagedResponse<SubscriberDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<SubscriberDto.Response> result = subscriberService.search(search, status, PageRequest.of(page, size, sort));
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<SubscriberDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(subscriberService.getById(id));
    }

    @GetMapping("/key/{subscriberKey}")
    public ApiResponse<SubscriberDto.Response> getByKey(@PathVariable String subscriberKey) {
        return ApiResponse.ok(subscriberService.getBySubscriberKey(subscriberKey));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriberDto.Response> create(@Valid @RequestBody SubscriberDto.CreateRequest request) {
        return ApiResponse.ok(subscriberService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubscriberDto.Response> update(@PathVariable String id,
                                                       @Valid @RequestBody SubscriberDto.UpdateRequest request) {
        return ApiResponse.ok(subscriberService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        subscriberService.delete(id);
    }

    @PostMapping("/bulk")
    public ApiResponse<SubscriberDto.BulkUpsertResponse> bulkUpsert(@Valid @RequestBody SubscriberDto.BulkUpsertRequest request) {
        return ApiResponse.ok(subscriberService.bulkUpsert(request));
    }

    @GetMapping("/count")
    public ApiResponse<Long> count() {
        return ApiResponse.ok(subscriberService.count());
    }
}
