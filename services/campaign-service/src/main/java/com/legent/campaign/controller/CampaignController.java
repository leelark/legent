package com.legent.campaign.controller;

import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.service.CampaignService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public PagedResponse<CampaignDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        Page<CampaignDto.Response> results = campaignService.search(search, PageRequest.of(page, size));
        return PagedResponse.of(results.getContent(), page, size, results.getTotalElements(), results.getTotalPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<CampaignDto.Response> getById(@PathVariable String id) {
        return ApiResponse.ok(campaignService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignDto.Response> create(@Valid @RequestBody CampaignDto.CreateRequest request) {
        return ApiResponse.ok(campaignService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CampaignDto.Response> update(@PathVariable String id,
                                                    @Valid @RequestBody CampaignDto.UpdateRequest request) {
        return ApiResponse.ok(campaignService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        campaignService.delete(id);
    }
}
