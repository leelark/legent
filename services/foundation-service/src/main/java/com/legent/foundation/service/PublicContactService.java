package com.legent.foundation.service;

import com.legent.foundation.domain.PublicContactRequest;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.repository.PublicContactRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicContactService {

    private static final String RECEIVED = "RECEIVED";

    private final PublicContactRequestRepository repository;

    @Transactional
    public PublicContactDto.Response submit(PublicContactDto.Request request) {
        PublicContactRequest contact = new PublicContactRequest();
        contact.setName(clean(request.getName()));
        contact.setWorkEmail(clean(request.getWorkEmail()).toLowerCase());
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

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
