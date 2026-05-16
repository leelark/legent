package com.legent.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.identity.domain.Account;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.User;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.AccountRepository;
import com.legent.identity.repository.AccountRoleBindingRepository;
import com.legent.identity.repository.FederationJdbcRepository;
import com.legent.identity.repository.TenantRepository;
import com.legent.identity.repository.UserRepository;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedIdentityServiceTest {

    private FederationJdbcRepository repository;
    private UserRepository userRepository;
    private TenantRepository tenantRepository;
    private AccountRepository accountRepository;
    private AccountMembershipRepository accountMembershipRepository;
    private AccountRoleBindingRepository accountRoleBindingRepository;
    private PasswordEncoder passwordEncoder;
    private FederatedIdentityService service;

    @BeforeEach
    void setUp() {
        repository = mock(FederationJdbcRepository.class);
        userRepository = mock(UserRepository.class);
        tenantRepository = mock(TenantRepository.class);
        accountRepository = mock(AccountRepository.class);
        accountMembershipRepository = mock(AccountMembershipRepository.class);
        accountRoleBindingRepository = mock(AccountRoleBindingRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new FederatedIdentityService(
                repository,
                userRepository,
                tenantRepository,
                accountRepository,
                accountMembershipRepository,
                accountRoleBindingRepository,
                passwordEncoder,
                mock(JwtTokenProvider.class),
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "serviceProviderEntityId", "legent-sp");
        when(tenantRepository.existsById("tenant-1")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId("user-saved");
            }
            return user;
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            if (account.getId() == null) {
                account.setId("account-saved");
            }
            return account;
        });
        when(accountMembershipRepository.save(any(AccountMembership.class))).thenAnswer(invocation -> {
            AccountMembership membership = invocation.getArgument(0);
            if (membership.getId() == null) {
                membership.setId("membership-saved");
            }
            return membership;
        });
    }

    @Test
    void upsertProvider_rejectsExternalHttpEndpoints() {
        FederationDto.ProviderRequest request = oidcRequest();
        request.setAuthorizationEndpoint("http://idp.example.com/oauth/authorize");

        assertThrows(IllegalArgumentException.class, () -> service.upsertProvider("tenant-1", request));
    }

    @Test
    void upsertProvider_redactsSamlSigningCertificate() {
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of());
        when(repository.insert(eq("federated_identity_providers"), anyMap(), anyList())).thenAnswer(invocation -> {
            Map<String, Object> saved = new LinkedHashMap<>(invocation.getArgument(1));
            saved.put("id", "provider-1");
            return saved;
        });

        Map<String, Object> saved = service.upsertProvider("tenant-1", samlRequest());

        assertFalse(saved.containsKey("signing_certificate"));
        assertTrue((Boolean) saved.get("hasSigningCertificate"));
        assertEquals("SAML", saved.get("protocol"));
    }

    @Test
    void startLogin_buildsEncodedOidcRedirectAndSanitizesReturnUrl() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", "provider-1");
        provider.put("tenant_id", "tenant-1");
        provider.put("provider_key", "okta");
        provider.put("protocol", "OIDC");
        provider.put("status", "ACTIVE");
        provider.put("authorization_endpoint", "https://idp.example.com/oauth/authorize");
        provider.put("client_id", "client-1");
        provider.put("redirect_uri", "https://legent.example.com/api/v1/sso/tenant-1/okta/oidc/callback");
        provider.put("scopes", List.of("openid", "email", "profile"));
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of(provider));
        when(repository.insert(eq("federation_login_states"), anyMap(), anyList())).thenReturn(Map.of());

        Map<String, Object> start = service.startLogin("tenant-1", "okta", "https://evil.example.com");

        assertTrue(String.valueOf(start.get("location")).contains("scope=openid%20email%20profile"));
        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("federation_login_states"), values.capture(), anyList());
        assertEquals("/app", values.getValue().get("redirect_after"));
    }

    @Test
    void provisionPrincipal_ignoresPrivilegedUnmappedFederatedGroup() {
        stubNewProvisioning("sso@example.com", "external-1");
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of());

        FederatedIdentityService.ProvisionedPrincipal principal = service.provisionPrincipal(
                provider(),
                attributes("sso@example.com", "external-1", List.of("ADMIN", "VIEWER")));

        assertFalse(principal.roles().contains("ADMIN"));
        assertTrue(principal.roles().contains("VIEWER"));
        assertTrue(principal.roles().contains("USER"));
    }

    @Test
    void provisionPrincipal_allowsPrivilegedRoleFromMappedFederatedGroup() {
        stubNewProvisioning("sso@example.com", "external-1");
        when(repository.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "display_name", "Okta Admins",
                "external_id", "group-1",
                "role_key", "ORG_ADMIN"
        )));

        FederatedIdentityService.ProvisionedPrincipal principal = service.provisionPrincipal(
                provider(),
                attributes("sso@example.com", "external-1", List.of("Okta Admins")));

        assertTrue(principal.roles().contains("ORG_ADMIN"));
    }

    @Test
    void provisionPrincipal_rejectsEmailFallbackForNativeUser() {
        User existing = user("native@example.com", null);
        when(userRepository.findByTenantIdAndIdentityProviderIdAndExternalId("tenant-1", "provider-1", "external-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "native@example.com"))
                .thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.provisionPrincipal(
                provider(),
                attributes("native@example.com", "external-1", List.of())));

        assertTrue(error.getMessage().contains("owned by another identity source"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionPrincipal_rejectsEmailFallbackForOtherProviderUser() {
        User existing = user("other@example.com", "provider-2");
        when(userRepository.findByTenantIdAndIdentityProviderIdAndExternalId("tenant-1", "provider-1", "external-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "other@example.com"))
                .thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.provisionPrincipal(
                provider(),
                attributes("other@example.com", "external-1", List.of())));

        assertTrue(error.getMessage().contains("owned by another identity source"));
        verify(userRepository, never()).save(any(User.class));
    }

    private FederationDto.ProviderRequest oidcRequest() {
        FederationDto.ProviderRequest request = new FederationDto.ProviderRequest();
        request.setProviderKey("okta");
        request.setDisplayName("Okta");
        request.setProtocol("OIDC");
        request.setIssuer("https://idp.example.com");
        request.setClientId("client-1");
        request.setAuthorizationEndpoint("https://idp.example.com/oauth/authorize");
        request.setTokenEndpoint("https://idp.example.com/oauth/token");
        request.setJwksUrl("https://idp.example.com/oauth/jwks");
        request.setRedirectUri("https://legent.example.com/api/v1/sso/tenant-1/okta/oidc/callback");
        return request;
    }

    private FederationDto.ProviderRequest samlRequest() {
        FederationDto.ProviderRequest request = new FederationDto.ProviderRequest();
        request.setProviderKey("azure");
        request.setDisplayName("Azure AD");
        request.setProtocol("SAML");
        request.setSsoUrl("https://idp.example.com/saml/sso");
        request.setRedirectUri("https://legent.example.com/api/v1/sso/tenant-1/azure/saml/acs");
        request.setSigningCertificate("-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----");
        request.setScimEnabled(true);
        return request;
    }

    private void stubNewProvisioning(String email, String externalId) {
        when(userRepository.findByTenantIdAndIdentityProviderIdAndExternalId("tenant-1", "provider-1", externalId))
                .thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", email))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(accountMembershipRepository.findByAccountIdAndTenantId("account-saved", "tenant-1"))
                .thenReturn(Optional.empty());
        when(accountRoleBindingRepository.findByMembershipId("membership-saved")).thenReturn(List.of());
    }

    private Map<String, Object> provider() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", "provider-1");
        provider.put("tenant_id", "tenant-1");
        provider.put("default_workspace_id", "workspace-default");
        provider.put("default_role_keys", List.of("USER"));
        provider.put("attribute_mapping", Map.of());
        provider.put("jit_provisioning_enabled", true);
        return provider;
    }

    private Map<String, Object> attributes(String email, String externalId, List<String> groups) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("email", email);
        attributes.put("externalId", externalId);
        attributes.put("given_name", "Sso");
        attributes.put("family_name", "User");
        attributes.put("groups", groups);
        return attributes;
    }

    private User user(String email, String providerId) {
        User user = new User();
        user.setId("existing-user");
        user.setTenantId("tenant-1");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole("USER");
        user.setIdentityProviderId(providerId);
        user.setActive(true);
        return user;
    }
}
