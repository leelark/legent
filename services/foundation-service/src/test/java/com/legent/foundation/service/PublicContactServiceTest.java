package com.legent.foundation.service;

import com.legent.foundation.domain.PublicContactRequest;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.repository.PublicContactRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicContactServiceTest {

    @Test
    void submitStoresTrimmedLowercaseContactRequest() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            PublicContactRequest saved = invocation.getArgument(0);
            saved.setId("01HXCONTACTREQUEST000000000");
            return saved;
        });

        PublicContactDto.Request request = new PublicContactDto.Request();
        request.setName(" Ada Lovelace ");
        request.setWorkEmail(" ADA@Example.COM ");
        request.setCompany(" Legent Labs ");
        request.setInterest(" Delivery review ");
        request.setMessage(" Need a launch plan. ");
        request.setSourcePage(" contact ");

        PublicContactDto.Response response = new PublicContactService(repository).submit(request);

        ArgumentCaptor<PublicContactRequest> captor = ArgumentCaptor.forClass(PublicContactRequest.class);
        Mockito.verify(repository).save(captor.capture());
        PublicContactRequest saved = captor.getValue();
        assertEquals("Ada Lovelace", saved.getName());
        assertEquals("ada@example.com", saved.getWorkEmail());
        assertEquals("Legent Labs", saved.getCompany());
        assertEquals("Delivery review", saved.getInterest());
        assertEquals("Need a launch plan.", saved.getMessage());
        assertEquals("contact", saved.getSourcePage());
        assertEquals("RECEIVED", saved.getStatus());
        assertEquals("RECEIVED", response.getStatus());
        assertNotNull(response.getMessage());
    }
}
