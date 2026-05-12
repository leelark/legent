package com.legent.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedIdentityServiceTest {

    private FederationJdbcRepository repository;
    private FederatedIdentityService service;

    @BeforeEach
    void setUp() {
        repository = mock(FederationJdbcRepository.class);
        service = new FederatedIdentityService(
                repository,
                mock(UserRepository.class),
                mock(TenantRepository.class),
                mock(AccountRepository.class),
                mock(AccountMembershipRepository.class),
                mock(AccountRoleBindingRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "serviceProviderEntityId", "legent-sp");
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
}
