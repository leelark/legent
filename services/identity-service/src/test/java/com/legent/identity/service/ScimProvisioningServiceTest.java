package com.legent.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.identity.domain.User;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.repository.FederationJdbcRepository;
import com.legent.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScimProvisioningServiceTest {

    private FederationJdbcRepository repository;
    private UserRepository userRepository;
    private ScimProvisioningService service;

    @BeforeEach
    void setUp() {
        repository = mock(FederationJdbcRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ScimProvisioningService(
                repository,
                mock(FederatedIdentityService.class),
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void listUsers_requiresBearerToken() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.listUsers(null, null, 1, 100));

        assertTrue(error.getMessage().contains("bearer token"));
    }

    @Test
    void listUsers_validatesScopeAndReturnsProviderScopedActiveUsers() throws Exception {
        String rawToken = "legent_scim_test";
        Map<String, Object> tokenRow = tokenRow(rawToken, List.of("scim:users"));
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of(tokenRow));
        when(repository.updateById(eq("federation_scim_tokens"), eq("token-1"), eq("tenant-1"), anyMap(), anyList()))
                .thenReturn(tokenRow);

        User active = user("user-1", "active@example.com", true);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndIsActiveTrueOrderByEmailAsc(
                eq("tenant-1"),
                eq("provider-1"),
                argThat(pageable -> pageable.getOffset() == 0L && pageable.getPageSize() == 100)))
                .thenReturn(List.of(active));

        Map<String, Object> response = service.listUsers("Bearer " + rawToken, null, 1, 100);

        assertEquals(1, response.get("totalResults"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.get("Resources");
        assertEquals("active@example.com", resources.get(0).get("userName"));
        verify(repository).queryForList(anyString(), eq(Map.of("hash", sha256(rawToken))));
        verify(userRepository, never()).findByTenantId("tenant-1");
    }

    @Test
    void listUsers_boundsPageRequestBeforeRepositoryQuery() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndIsActiveTrueOrderByEmailAsc(
                eq("tenant-1"),
                eq("provider-1"),
                argThat(pageable -> pageable.getOffset() == 4L && pageable.getPageSize() == 200)))
                .thenReturn(List.of());

        Map<String, Object> response = service.listUsers("Bearer " + rawToken, null, 5, 5_000);

        assertEquals(5, response.get("startIndex"));
        assertEquals(0, response.get("totalResults"));
        verify(userRepository, never()).findByTenantId("tenant-1");
    }

    @Test
    void listUsers_normalizesInvalidBoundsBeforeRepositoryQuery() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndIsActiveTrueOrderByEmailAsc(
                eq("tenant-1"),
                eq("provider-1"),
                argThat(pageable -> pageable.getOffset() == 0L && pageable.getPageSize() == 1)))
                .thenReturn(List.of());

        Map<String, Object> response = service.listUsers("Bearer " + rawToken, null, -20, 0);

        assertEquals(1, response.get("startIndex"));
        assertEquals(0, response.get("totalResults"));
        verify(userRepository, never()).findByTenantId("tenant-1");
    }

    @Test
    void listUsers_emailFilterUsesProviderScopedLookup() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        User active = user("user-1", "active@example.com", true);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndEmailIgnoreCaseAndIsActiveTrue(
                "tenant-1",
                "provider-1",
                "active@example.com"))
                .thenReturn(Optional.of(active));

        Map<String, Object> response = service.listUsers("Bearer " + rawToken,
                "userName eq \"ACTIVE@example.com\"",
                1,
                100);

        assertEquals(1, response.get("totalResults"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.get("Resources");
        assertEquals("active@example.com", resources.get(0).get("userName"));
        verify(userRepository, never()).findByTenantId("tenant-1");
        verify(userRepository, never()).findByTenantIdAndEmailIgnoreCase(anyString(), anyString());
    }

    @Test
    void listUsers_emailFilterRejectsUserOwnedByAnotherProvider() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndEmailIgnoreCaseAndIsActiveTrue(
                "tenant-1",
                "provider-1",
                "other@example.com"))
                .thenReturn(Optional.empty());

        Map<String, Object> response = service.listUsers("Bearer " + rawToken,
                "userName eq \"other@example.com\"",
                1,
                100);

        assertEquals(0, response.get("totalResults"));
        verify(userRepository, never()).findByTenantId("tenant-1");
        verify(userRepository, never()).findByTenantIdAndEmailIgnoreCase(anyString(), anyString());
    }

    @Test
    void getUser_rejectsUserOwnedByAnotherProvider() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndId("tenant-1", "user-2"))
                .thenReturn(Optional.of(user("user-2", "other@example.com", true, "provider-2")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.getUser("Bearer " + rawToken, "user-2"));

        assertTrue(error.getMessage().contains("SCIM user not found"));
    }

    @Test
    void replaceUser_rejectsNativeUser() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndId("tenant-1", "user-2"))
                .thenReturn(Optional.of(user("user-2", "native@example.com", true, null)));

        FederationDto.ScimUserRequest request = new FederationDto.ScimUserRequest();
        request.setUserName("native@example.com");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replaceUser("Bearer " + rawToken, "user-2", request));

        assertTrue(error.getMessage().contains("SCIM user not found"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUser_rejectsUserOwnedByAnotherProvider() throws Exception {
        String rawToken = "legent_scim_test";
        stubToken(rawToken);
        when(userRepository.findByTenantIdAndId("tenant-1", "user-2"))
                .thenReturn(Optional.of(user("user-2", "other@example.com", true, "provider-2")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.deleteUser("Bearer " + rawToken, "user-2"));

        assertTrue(error.getMessage().contains("SCIM user not found"));
        verify(userRepository, never()).save(any(User.class));
    }

    private void stubToken(String rawToken) throws Exception {
        Map<String, Object> tokenRow = tokenRow(rawToken, List.of("scim:users"));
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of(tokenRow));
        when(repository.updateById(eq("federation_scim_tokens"), eq("token-1"), eq("tenant-1"), anyMap(), anyList()))
                .thenReturn(tokenRow);
    }

    private Map<String, Object> tokenRow(String rawToken, List<String> scopes) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "token-1");
        row.put("tenant_id", "tenant-1");
        row.put("provider_id", "provider-1");
        row.put("provider_key", "okta");
        row.put("protocol", "OIDC");
        row.put("scopes", scopes);
        row.put("default_workspace_id", "workspace-default");
        row.put("default_role_keys", List.of("USER"));
        row.put("attribute_mapping", Map.of());
        row.put("jit_provisioning_enabled", true);
        row.put("token_hash", sha256(rawToken));
        return row;
    }

    private User user(String id, String email, boolean active) {
        return user(id, email, active, "provider-1");
    }

    private User user(String id, String email, boolean active, String providerId) {
        User user = new User();
        user.setId(id);
        user.setTenantId("tenant-1");
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setActive(active);
        user.setIdentityProviderId(providerId);
        return user;
    }

    private String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
