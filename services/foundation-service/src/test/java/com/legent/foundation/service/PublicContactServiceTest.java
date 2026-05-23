package com.legent.foundation.service;

import com.legent.foundation.domain.PublicContactRequest;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.repository.PublicContactRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicContactServiceTest {

    @Test
    void submitStoresTrimmedLowercaseContactRequest() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            PublicContactRequest saved = invocation.getArgument(0);
            saved.setId("01HXCONTACTREQUEST000000000");
            return saved;
        });

        PublicContactDto.Request request = validRequest();
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

    @Test
    void submitNormalizesPublicTextWhitespaceBeforePersisting() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> {
            PublicContactRequest saved = invocation.getArgument(0);
            saved.setId("01HXCONTACTREQUEST000000003");
            return saved;
        });

        PublicContactDto.Request request = validRequest();
        request.setName("\tAda\nLovelace ");
        request.setWorkEmail("\r\nADA@Example.COM\t");
        request.setCompany("Legent\r\nLabs");
        request.setInterest("Delivery\u00A0review");
        request.setMessage("Need\r\nlaunch\tplan where 2 < 3 and 5 > 4.");
        request.setSourcePage(" pricing\ncontact ");

        new PublicContactService(repository).submit(request);

        ArgumentCaptor<PublicContactRequest> captor = ArgumentCaptor.forClass(PublicContactRequest.class);
        Mockito.verify(repository).save(captor.capture());
        PublicContactRequest saved = captor.getValue();
        assertEquals("Ada Lovelace", saved.getName());
        assertEquals("ada@example.com", saved.getWorkEmail());
        assertEquals("Legent Labs", saved.getCompany());
        assertEquals("Delivery review", saved.getInterest());
        assertEquals("Need launch plan where 2 < 3 and 5 > 4.", saved.getMessage());
        assertEquals("pricing contact", saved.getSourcePage());
    }

    @Test
    void submitRejectsHtmlPublicTextBeforePersisting() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        PublicContactService service = new PublicContactService(repository);

        PublicContactDto.Request rawHtml = validRequest();
        rawHtml.setMessage("Hello <script>alert(1)</script>");
        assertThrows(IllegalArgumentException.class, () -> service.submit(rawHtml));

        PublicContactDto.Request encodedHtml = validRequest();
        encodedHtml.setCompany("&lt;img src=x onerror=alert(1)&gt;");
        assertThrows(IllegalArgumentException.class, () -> service.submit(encodedHtml));

        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void submitRejectsUnsupportedControlCharactersBeforePersisting() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        PublicContactService service = new PublicContactService(repository);

        PublicContactDto.Request nullByte = validRequest();
        nullByte.setName("Ada\u0000Lovelace");
        assertThrows(IllegalArgumentException.class, () -> service.submit(nullByte));

        PublicContactDto.Request bidiOverride = validRequest();
        bidiOverride.setMessage("Need launch plan\u202E");
        assertThrows(IllegalArgumentException.class, () -> service.submit(bidiOverride));

        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void listAdminFiltersByStatusAndMapsRows() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        PublicContactRequest row = new PublicContactRequest();
        row.setId("01HXCONTACTREQUEST000000001");
        row.setWorkEmail("buyer@example.com");
        row.setCompany("Buyer Co");
        row.setMessage("Need plan");
        row.setStatus("IN_REVIEW");
        Mockito.when(repository.findByStatusOrderByCreatedAtDesc(Mockito.eq("IN_REVIEW"), Mockito.any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        var page = new PublicContactService(repository).listAdmin("in_review", PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("buyer@example.com", page.getContent().get(0).getWorkEmail());
        assertEquals("IN_REVIEW", page.getContent().get(0).getStatus());
    }

    @Test
    void updateStatusAllowsKnownAdminStatuses() {
        PublicContactRequestRepository repository = Mockito.mock(PublicContactRequestRepository.class);
        PublicContactRequest row = new PublicContactRequest();
        row.setId("01HXCONTACTREQUEST000000002");
        row.setWorkEmail("buyer@example.com");
        row.setCompany("Buyer Co");
        row.setMessage("Need plan");
        row.setStatus("RECEIVED");
        Mockito.when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        PublicContactDto.StatusUpdateRequest request = new PublicContactDto.StatusUpdateRequest();
        request.setStatus("contacted");
        var response = new PublicContactService(repository).updateStatus(row.getId(), request);

        assertEquals("CONTACTED", response.getStatus());
        Mockito.verify(repository).save(row);
    }

    private static PublicContactDto.Request validRequest() {
        PublicContactDto.Request request = new PublicContactDto.Request();
        request.setName("Ada Lovelace");
        request.setWorkEmail("ada@example.com");
        request.setCompany("Legent Labs");
        request.setInterest("Delivery review");
        request.setMessage("Need a launch plan.");
        request.setSourcePage("contact");
        return request;
    }
}
