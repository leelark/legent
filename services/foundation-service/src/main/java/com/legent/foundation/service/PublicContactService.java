package com.legent.foundation.service;

import com.legent.foundation.domain.PublicContactRequest;
import com.legent.foundation.dto.PublicContactDto;
import com.legent.foundation.repository.PublicContactRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PublicContactService {

    private static final String RECEIVED = "RECEIVED";
    private static final Set<String> ADMIN_STATUSES = Set.of(RECEIVED, "IN_REVIEW", "CONTACTED", "CLOSED");
    private static final Pattern HTML_MARKUP_PATTERN = Pattern.compile(
            "(?is)<!--|<!\\s*doctype|<\\?xml|<\\s*/?\\s*[a-z][a-z0-9:-]*(?:\\s+[^<>]*)?\\s*/?>");
    private static final Set<Integer> UNSAFE_INVISIBLE_CONTROLS = Set.of(
            0x061C, 0x200B, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2066, 0x2067, 0x2068, 0x2069, 0xFEFF);

    private final PublicContactRequestRepository repository;

    @Transactional
    public PublicContactDto.Response submit(PublicContactDto.Request request) {
        String name = cleanPublicText(request.getName(), "Name", 120, false);
        String workEmail = cleanPublicText(request.getWorkEmail(), "Work email", 255, true);
        String company = cleanPublicText(request.getCompany(), "Company", 160, true);
        String interest = cleanPublicText(request.getInterest(), "Interest", 120, false);
        String message = cleanPublicText(request.getMessage(), "Message", 2000, true);
        String sourcePage = cleanPublicText(request.getSourcePage(), "Source page", 120, false);

        PublicContactRequest contact = new PublicContactRequest();
        contact.setName(name);
        contact.setWorkEmail(workEmail.toLowerCase(Locale.ROOT));
        contact.setCompany(company);
        contact.setInterest(interest);
        contact.setMessage(message);
        contact.setSourcePage(sourcePage);
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

    private String cleanPublicText(String value, String fieldName, int maxLength, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        rejectHtmlMarkup(normalized, fieldName);

        StringBuilder cleaned = new StringBuilder(normalized.length());
        boolean previousWasSpace = false;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isNormalizableWhitespace(codePoint)) {
                if (!previousWasSpace) {
                    cleaned.append(' ');
                    previousWasSpace = true;
                }
                continue;
            }
            if (Character.isISOControl(codePoint) || UNSAFE_INVISIBLE_CONTROLS.contains(codePoint)) {
                throw new IllegalArgumentException(fieldName + " contains unsupported control characters");
            }
            cleaned.appendCodePoint(codePoint);
            previousWasSpace = false;
        }

        String result = cleaned.toString().trim();
        if (required && result.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return result;
    }

    private boolean isNormalizableWhitespace(int codePoint) {
        return codePoint == ' '
                || codePoint == '\t'
                || codePoint == '\n'
                || codePoint == '\r'
                || Character.isSpaceChar(codePoint);
    }

    private void rejectHtmlMarkup(String value, String fieldName) {
        String htmlDecoded = HtmlUtils.htmlUnescape(value);
        if (HTML_MARKUP_PATTERN.matcher(value).find() || HTML_MARKUP_PATTERN.matcher(htmlDecoded).find()) {
            throw new IllegalArgumentException(fieldName + " cannot contain HTML content");
        }
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
