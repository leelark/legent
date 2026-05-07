package com.legent.foundation.service;

import com.legent.foundation.domain.PublicContactRequest;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.repository.PublicContactRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PublicContactService {

    private static final String RECEIVED = "RECEIVED";
    private static final Set<String> ADMIN_STATUSES = Set.of(RECEIVED, "IN_REVIEW", "CONTACTED", "CLOSED");

    private final PublicContactRequestRepository repository;

    @Transactional
    public PublicContactDto.Response submit(PublicContactDto.Request request) {
        PublicContactRequest contact = new PublicContactRequest();
        contact.setName(clean(request.getName()));
        contact.setWorkEmail(clean(request.getWorkEmail()).toLowerCase(Locale.ROOT));
        contact.setCompany(clean(request.getCompany()));
        contact.setInterest(clean(request.getInterest()));
        contact.setMessage(clean(request.getMessage()));
        contact.setSourcePage(clean(request.getSourcePage()));
        contact.setStatus(RECEIVED);

        PublicContactRequest saved = repository.save(contact);
        return PublicContactDto.Response.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .message("Request received. A Legent specialist will follow up shortly.")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PublicContactDto.AdminResponse> listAdmin(String status, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status, false);
        Page<PublicContactRequest> page = normalizedStatus == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(normalizedStatus, pageable);
        return page.map(this::toAdminResponse);
    }

    @Transactional
    public PublicContactDto.AdminResponse updateStatus(String id, PublicContactDto.StatusUpdateRequest request) {
        PublicContactRequest contact = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Public contact request not found"));
        contact.setStatus(normalizeStatus(request.getStatus(), true));
        return toAdminResponse(repository.save(contact));
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeStatus(String status, boolean required) {
        if (status == null || status.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Status is required");
            }
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ADMIN_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported contact request status: " + status);
        }
        return normalized;
    }

    private PublicContactDto.AdminResponse toAdminResponse(PublicContactRequest contact) {
        return PublicContactDto.AdminResponse.builder()
                .id(contact.getId())
                .name(contact.getName())
                .workEmail(contact.getWorkEmail())
                .company(contact.getCompany())
                .interest(contact.getInterest())
                .message(contact.getMessage())
                .sourcePage(contact.getSourcePage())
                .status(contact.getStatus())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }
}
