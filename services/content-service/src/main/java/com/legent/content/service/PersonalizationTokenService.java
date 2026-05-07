package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.PersonalizationToken;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.PersonalizationTokenRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PersonalizationTokenService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Pattern TOKEN_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$");

    private final PersonalizationTokenRepository tokenRepository;
    private final EmailContentValidationService validationService;

    @Transactional
    public PersonalizationToken create(String tenantId, EmailStudioDto.TokenRequest request) {
        validateTokenKey(request.getTokenKey());
        if (tokenRepository.existsByTenantIdAndTokenKeyAndDeletedAtIsNull(tenantId, request.getTokenKey())) {
            throw new ConflictException("Personalization token already exists: " + request.getTokenKey());
        }
        PersonalizationToken token = new PersonalizationToken();
        token.setTenantId(tenantId);
        apply(token, request);
        return tokenRepository.save(token);
    }

    @Transactional
    public PersonalizationToken update(String tenantId, String id, EmailStudioDto.TokenRequest request) {
        PersonalizationToken token = get(tenantId, id);
        if (request.getTokenKey() != null && !request.getTokenKey().equals(token.getTokenKey())) {
            validateTokenKey(request.getTokenKey());
            if (tokenRepository.existsByTenantIdAndTokenKeyAndDeletedAtIsNull(tenantId, request.getTokenKey())) {
                throw new ConflictException("Personalization token already exists: " + request.getTokenKey());
            }
        }
        apply(token, request);
        return tokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public PersonalizationToken get(String tenantId, String id) {
        return tokenRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new NotFoundException("PersonalizationToken", id));
    }

    @Transactional(readOnly = true)
    public Page<PersonalizationToken> list(String tenantId, Pageable pageable) {
        return tokenRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        PersonalizationToken token = get(tenantId, id);
        token.setDeletedAt(Instant.now());
        tokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public TokenRenderResult render(String tenantId, String content, Map<String, Object> variables, boolean htmlContext) {
        if (content == null || content.isBlank()) {
            return new TokenRenderResult(content == null ? "" : content, List.of(), List.of(), List.of());
        }
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        StringBuffer rendered = new StringBuffer();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> tokenKeys = new LinkedHashSet<>();
        while (matcher.find()) {
            String tokenKey = matcher.group(1);
            if (isReservedToken(tokenKey)) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            tokenKeys.add(tokenKey);
            PersonalizationToken token = tokenRepository.findByTenantIdAndTokenKeyAndDeletedAtIsNull(tenantId, tokenKey)
                    .orElse(null);
            if (token == null) {
                errors.add("Unknown personalization token: " + tokenKey);
                matcher.appendReplacement(rendered, "");
                continue;
            }
            Object rawValue = lookupValue(token, variables);
            String resolved = rawValue == null ? null : String.valueOf(rawValue);
            if (resolved == null || resolved.isBlank()) {
                if (token.getDefaultValue() != null && !token.getDefaultValue().isBlank()) {
                    resolved = token.getDefaultValue();
                } else if (token.getSampleValue() != null && !token.getSampleValue().isBlank()) {
                    resolved = token.getSampleValue();
                    warnings.add("Using sample value for token: " + tokenKey);
                } else if (Boolean.TRUE.equals(token.getRequired())) {
                    errors.add("Required personalization token is missing: " + tokenKey);
                    resolved = "";
                } else {
                    resolved = "";
                }
            }
            String safeValue = htmlContext ? validationService.escapeHtml(resolved) : stripSubjectUnsafeCharacters(resolved);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(safeValue));
        }
        matcher.appendTail(rendered);
        return new TokenRenderResult(rendered.toString(), tokenKeys.stream().toList(), errors, warnings);
    }

    public Set<String> extractTokenKeys(String content) {
        Set<String> tokenKeys = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return tokenKeys;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            String tokenKey = matcher.group(1);
            if (!isReservedToken(tokenKey)) {
                tokenKeys.add(tokenKey);
            }
        }
        return tokenKeys;
    }

    private void apply(PersonalizationToken token, EmailStudioDto.TokenRequest request) {
        if (request.getTokenKey() != null) token.setTokenKey(request.getTokenKey().trim());
        if (request.getDisplayName() != null) token.setDisplayName(request.getDisplayName().trim());
        if (request.getDescription() != null) token.setDescription(request.getDescription());
        if (request.getSourceType() != null) token.setSourceType(request.getSourceType().trim().toUpperCase(Locale.ROOT));
        if (request.getDataPath() != null) token.setDataPath(request.getDataPath().trim());
        if (request.getDefaultValue() != null) token.setDefaultValue(request.getDefaultValue());
        if (request.getSampleValue() != null) token.setSampleValue(request.getSampleValue());
        if (request.getRequired() != null) token.setRequired(request.getRequired());
    }

    private void validateTokenKey(String tokenKey) {
        if (tokenKey == null || tokenKey.isBlank() || !TOKEN_KEY_PATTERN.matcher(tokenKey).matches()) {
            throw new ValidationException("tokenKey", "Use letters, numbers, dots, underscores, and dashes; key must start with a letter");
        }
    }

    private Object lookupValue(PersonalizationToken token, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        String path = token.getDataPath() != null && !token.getDataPath().isBlank()
                ? token.getDataPath()
                : token.getTokenKey();
        return lookupPath(variables, path);
    }

    @SuppressWarnings("unchecked")
    private Object lookupPath(Map<String, Object> variables, String path) {
        Object current = variables;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String stripSubjectUnsafeCharacters(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private boolean isReservedToken(String tokenKey) {
        return tokenKey.startsWith("snippet.")
                || tokenKey.startsWith("dynamic.")
                || tokenKey.startsWith("block.")
                || tokenKey.startsWith("brand.");
    }

    public record TokenRenderResult(String content, List<String> tokenKeys, List<String> errors, List<String> warnings) {}
}
