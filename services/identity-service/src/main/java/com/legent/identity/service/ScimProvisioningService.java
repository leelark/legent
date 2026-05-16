package com.legent.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.identity.domain.User;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.repository.FederationJdbcRepository;
import com.legent.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScimProvisioningService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final FederationJdbcRepository repository;
    private final FederatedIdentityService federatedIdentityService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> serviceProviderConfig() {
        return Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
                "patch", Map.of("supported", true),
                "bulk", Map.of("supported", false),
                "filter", Map.of("supported", true, "maxResults", 200),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", false),
                "authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "Bearer Token",
                        "description", "SCIM bearer token generated from Legent federation admin console"
                ))
        );
    }

    public Map<String, Object> resourceTypes() {
        return listResponse(List.of(
                Map.of("id", "User", "name", "User", "endpoint", "/Users", "schema", "urn:ietf:params:scim:schemas:core:2.0:User"),
                Map.of("id", "Group", "name", "Group", "endpoint", "/Groups", "schema", "urn:ietf:params:scim:schemas:core:2.0:Group")
        ), 1);
    }

    public Map<String, Object> schemas() {
        return listResponse(List.of(
                Map.of("id", "urn:ietf:params:scim:schemas:core:2.0:User", "name", "User"),
                Map.of("id", "urn:ietf:params:scim:schemas:core:2.0:Group", "name", "Group"),
                Map.of("id", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", "name", "EnterpriseUser")
        ), 1);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUsers(String authorization, String filter, int startIndex, int count) {
        ScimContext context = authenticate(authorization, "scim:users");
        String emailFilter = parseUserNameFilter(filter);
        List<User> users = emailFilter == null
                ? userRepository.findByTenantId(context.tenantId())
                : userRepository.findByTenantIdAndEmailIgnoreCase(context.tenantId(), emailFilter).map(List::of).orElse(List.of());
        List<Map<String, Object>> resources = users.stream()
                .filter(User::isActive)
                .filter(user -> isOwnedByProvider(user, context))
                .skip(Math.max(0, startIndex - 1L))
                .limit(Math.max(1, Math.min(count, 200)))
                .map(user -> toScimUser(user, context))
                .toList();
        return listResponse(resources, startIndex);
    }

    @Transactional
    public Map<String, Object> createUser(String authorization, FederationDto.ScimUserRequest request) {
        ScimContext context = authenticate(authorization, "scim:users");
        Map<String, Object> attrs = attributesFromScim(request);
        FederatedIdentityService.ProvisionedPrincipal principal = federatedIdentityService.provisionPrincipal(context.provider(), attrs);
        if (Boolean.FALSE.equals(request.getActive())) {
            principal.user().setActive(false);
            userRepository.save(principal.user());
        }
        return toScimUser(principal.user(), context);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUser(String authorization, String id) {
        ScimContext context = authenticate(authorization, "scim:users");
        User user = findUser(context, id);
        return toScimUser(user, context);
    }

    @Transactional
    public Map<String, Object> replaceUser(String authorization, String id, FederationDto.ScimUserRequest request) {
        ScimContext context = authenticate(authorization, "scim:users");
        User user = findUser(context, id);
        applyScimUser(user, request, context);
        return toScimUser(userRepository.save(user), context);
    }

    @Transactional
    public Map<String, Object> patchUser(String authorization, String id, FederationDto.ScimPatchRequest request) {
        ScimContext context = authenticate(authorization, "scim:users");
        User user = findUser(context, id);
        if (request.getOperations() != null) {
            for (FederationDto.ScimPatchOperation op : request.getOperations()) {
                applyPatch(user, op);
            }
        }
        return toScimUser(userRepository.save(user), context);
    }

    @Transactional
    public void deleteUser(String authorization, String id) {
        ScimContext context = authenticate(authorization, "scim:users");
        User user = findUser(context, id);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listGroups(String authorization, int startIndex, int count) {
        ScimContext context = authenticate(authorization, "scim:groups");
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM federation_scim_groups
                WHERE tenant_id = :tenantId AND provider_id = :providerId AND deleted_at IS NULL
                ORDER BY display_name ASC
                LIMIT :count OFFSET :offset
                """, Map.of(
                "tenantId", context.tenantId(),
                "providerId", context.provider().get("id"),
                "count", Math.max(1, Math.min(count, 200)),
                "offset", Math.max(0, startIndex - 1)));
        return listResponse(rows.stream().map(this::toScimGroup).toList(), startIndex);
    }

    @Transactional
    public Map<String, Object> createGroup(String authorization, FederationDto.ScimGroupRequest request) {
        ScimContext context = authenticate(authorization, "scim:groups");
        Map<String, Object> values = baseValues(context.tenantId());
        values.put("provider_id", context.provider().get("id"));
        values.put("external_id", blankToNull(request.getExternalId()));
        values.put("display_name", request.getDisplayName().trim());
        values.put("role_key", request.getDisplayName().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_"));
        values.put("members", toJson(request.getMembers() == null ? List.of() : request.getMembers()));
        return toScimGroup(repository.insert("federation_scim_groups", values, List.of("members")));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGroup(String authorization, String id) {
        ScimContext context = authenticate(authorization, "scim:groups");
        return toScimGroup(findGroup(context, id));
    }

    @Transactional
    public Map<String, Object> replaceGroup(String authorization, String id, FederationDto.ScimGroupRequest request) {
        ScimContext context = authenticate(authorization, "scim:groups");
        findGroup(context, id);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("external_id", blankToNull(request.getExternalId()));
        updates.put("display_name", request.getDisplayName().trim());
        updates.put("role_key", request.getDisplayName().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_"));
        updates.put("members", toJson(request.getMembers() == null ? List.of() : request.getMembers()));
        return toScimGroup(repository.updateById("federation_scim_groups", id, context.tenantId(), updates, List.of("members")));
    }

    @Transactional
    public void deleteGroup(String authorization, String id) {
        ScimContext context = authenticate(authorization, "scim:groups");
        findGroup(context, id);
        repository.updateById("federation_scim_groups", id, context.tenantId(), Map.of("deleted_at", Instant.now()), List.of());
    }

    ScimContext authenticate(String authorization, String requiredScope) {
        String token = bearer(authorization);
        String hash = sha256(token);
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT token.*, provider.provider_key, provider.protocol, provider.status AS provider_status,
                       provider.default_workspace_id, provider.default_role_keys, provider.attribute_mapping,
                       provider.jit_provisioning_enabled, provider.scim_enabled
                FROM federation_scim_tokens token
                JOIN federated_identity_providers provider ON provider.id = token.provider_id
                WHERE token.token_hash = :hash
                  AND token.status = 'ACTIVE'
                  AND provider.status = 'ACTIVE'
                  AND provider.scim_enabled = TRUE
                  AND token.deleted_at IS NULL
                  AND provider.deleted_at IS NULL
                  AND (token.expires_at IS NULL OR token.expires_at > NOW())
                LIMIT 1
                """, Map.of("hash", hash));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Invalid SCIM bearer token");
        }
        Map<String, Object> row = rows.get(0);
        List<String> scopes = readStringList(row.get("scopes"));
        if (!scopes.contains(requiredScope) && !scopes.contains("scim:*")) {
            throw new IllegalArgumentException("SCIM token lacks scope " + requiredScope);
        }
        repository.updateById("federation_scim_tokens", String.valueOf(row.get("id")), String.valueOf(row.get("tenant_id")),
                Map.of("last_used_at", Instant.now()), List.of());
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", row.get("provider_id"));
        provider.put("tenant_id", row.get("tenant_id"));
        provider.put("provider_key", row.get("provider_key"));
        provider.put("protocol", row.get("protocol"));
        provider.put("default_workspace_id", row.get("default_workspace_id"));
        provider.put("default_role_keys", row.get("default_role_keys"));
        provider.put("attribute_mapping", row.get("attribute_mapping"));
        provider.put("jit_provisioning_enabled", row.get("jit_provisioning_enabled"));
        return new ScimContext(String.valueOf(row.get("tenant_id")), provider, scopes);
    }

    private Map<String, Object> attributesFromScim(FederationDto.ScimUserRequest request) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("externalId", firstNonBlank(request.getExternalId(), request.getId(), request.getUserName()));
        attrs.put("email", primaryEmail(request));
        attrs.put("userName", request.getUserName());
        attrs.put("given_name", request.getName() == null ? null : request.getName().get("givenName"));
        attrs.put("family_name", request.getName() == null ? null : request.getName().get("familyName"));
        attrs.put("groups", groupValues(request.getGroups()));
        return attrs;
    }

    private void applyScimUser(User user, FederationDto.ScimUserRequest request, ScimContext context) {
        user.setEmail(primaryEmail(request).toLowerCase(Locale.ROOT));
        if (request.getName() != null) {
            user.setFirstName(asString(request.getName().get("givenName")));
            user.setLastName(asString(request.getName().get("familyName")));
        }
        user.setExternalId(firstNonBlank(request.getExternalId(), user.getExternalId(), request.getUserName()));
        user.setIdentityProviderId(String.valueOf(context.provider().get("id")));
        user.setActive(request.getActive() == null || request.getActive());
    }

    @SuppressWarnings("unchecked")
    private void applyPatch(User user, FederationDto.ScimPatchOperation op) {
        if (op == null || op.getOp() == null) {
            return;
        }
        String path = op.getPath() == null ? "" : op.getPath();
        Object value = op.getValue();
        if ("active".equalsIgnoreCase(path)) {
            user.setActive(Boolean.parseBoolean(String.valueOf(value)));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object active = map.get("active");
            if (active != null) {
                user.setActive(Boolean.parseBoolean(String.valueOf(active)));
            }
            Object userName = map.get("userName");
            if (userName != null) {
                user.setEmail(String.valueOf(userName).toLowerCase(Locale.ROOT));
            }
            Object name = map.get("name");
            if (name instanceof Map<?, ?> nameMap) {
                Object given = nameMap.get("givenName");
                Object family = nameMap.get("familyName");
                if (given != null) {
                    user.setFirstName(String.valueOf(given));
                }
                if (family != null) {
                    user.setLastName(String.valueOf(family));
                }
            }
        }
    }

    private User findUser(ScimContext context, String id) {
        return userRepository.findByTenantIdAndId(context.tenantId(), id)
                .filter(user -> isOwnedByProvider(user, context))
                .orElseThrow(() -> new IllegalArgumentException("SCIM user not found: " + id));
    }

    private boolean isOwnedByProvider(User user, ScimContext context) {
        return String.valueOf(context.provider().get("id")).equals(user.getIdentityProviderId());
    }

    private Map<String, Object> findGroup(ScimContext context, String id) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM federation_scim_groups
                WHERE tenant_id = :tenantId AND provider_id = :providerId AND id = :id AND deleted_at IS NULL
                LIMIT 1
                """, Map.of("tenantId", context.tenantId(), "providerId", context.provider().get("id"), "id", id));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("SCIM group not found: " + id);
        }
        return rows.get(0);
    }

    private Map<String, Object> toScimUser(User user, ScimContext context) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        resource.put("id", user.getId());
        resource.put("externalId", firstNonBlank(user.getExternalId(), user.getEmail()));
        resource.put("userName", user.getEmail());
        resource.put("name", Map.of(
                "givenName", user.getFirstName() == null ? "" : user.getFirstName(),
                "familyName", user.getLastName() == null ? "" : user.getLastName()
        ));
        resource.put("displayName", List.of(user.getFirstName(), user.getLastName()).stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(user.getEmail()));
        resource.put("active", user.isActive());
        resource.put("emails", List.of(Map.of("value", user.getEmail(), "primary", true, "type", "work")));
        resource.put("meta", Map.of(
                "resourceType", "User",
                "created", user.getCreatedAt() == null ? Instant.now().toString() : user.getCreatedAt().toString(),
                "lastModified", user.getUpdatedAt() == null ? Instant.now().toString() : user.getUpdatedAt().toString(),
                "location", "/api/v1/scim/v2/Users/" + user.getId()
        ));
        resource.put("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", Map.of(
                "tenantId", context.tenantId(),
                "identityProviderId", context.provider().get("id")
        ));
        return resource;
    }

    private Map<String, Object> toScimGroup(Map<String, Object> row) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"));
        group.put("id", row.get("id"));
        group.put("externalId", row.get("external_id"));
        group.put("displayName", row.get("display_name"));
        group.put("members", readMapList(row.get("members")));
        group.put("meta", Map.of("resourceType", "Group", "location", "/api/v1/scim/v2/Groups/" + row.get("id")));
        return group;
    }

    private Map<String, Object> listResponse(List<Map<String, Object>> resources, int startIndex) {
        return Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
                "totalResults", resources.size(),
                "startIndex", Math.max(1, startIndex),
                "itemsPerPage", resources.size(),
                "Resources", resources
        );
    }

    private String primaryEmail(FederationDto.ScimUserRequest request) {
        if (request.getEmails() != null) {
            for (Map<String, Object> email : request.getEmails()) {
                Object value = email.get("value");
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        }
        return request.getUserName();
    }

    private List<String> groupValues(List<Map<String, Object>> groups) {
        if (groups == null) {
            return List.of();
        }
        return groups.stream()
                .map(group -> firstNonBlank(asString(group.get("display")), asString(group.get("value"))))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String parseUserNameFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        String trimmed = filter.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("username eq ")) {
            return null;
        }
        return trimmed.substring("userName eq ".length()).replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new IllegalArgumentException("SCIM bearer token required");
        }
        return authorization.substring(7).trim();
    }

    private Map<String, Object> baseValues(String tenantId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenantId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", "SCIM");
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((key, val) -> row.put(String.valueOf(key), val));
                    rows.add(row);
                }
            }
            return rows;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize SCIM payload", e);
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash SCIM token", e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    record ScimContext(String tenantId, Map<String, Object> provider, List<String> scopes) {
    }
}
