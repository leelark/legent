package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.Email;
import com.legent.content.dto.EmailDto;
import com.legent.content.repository.EmailRepository;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailRepository emailRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailDto.Response> createEmail(@Valid @RequestBody EmailDto.Create request) {
        String tenantId = TenantContext.requireTenantId();
        Email email = new Email();
        email.setTenantId(tenantId);
        email.setName(request.getName());
        email.setSubject(request.getSubject());
        email.setBody(request.getBody());
        email.setTemplateId(request.getTemplateId());
        email.setStatus(Email.EmailStatus.DRAFT);
        Email saved = emailRepository.save(email);
        return ApiResponse.ok(mapToResponse(saved));
    }

    @GetMapping("/recent")
    public ApiResponse<List<EmailDto.Response>> getRecentEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<Email> emails = emailRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return ApiResponse.ok(emails.getContent().stream().map(this::mapToResponse).toList());
    }

    @GetMapping
    public PagedResponse<EmailDto.Response> listEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<Email> emails = emailRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                emails.getContent().stream().map(this::mapToResponse).toList(),
                page,
                size,
                emails.getTotalElements(),
                emails.getTotalPages()
        );
    }

    private EmailDto.Response mapToResponse(Email email) {
        EmailDto.Response response = new EmailDto.Response();
        response.setId(email.getId());
        response.setName(email.getName());
        response.setSubject(email.getSubject());
        response.setBody(email.getBody());
        response.setTemplateId(email.getTemplateId());
        response.setStatus(email.getStatus().name());
        if (email.getCreatedAt() != null) {
            response.setCreatedAt(email.getCreatedAt().toString());
        }
        return response;
    }
}
