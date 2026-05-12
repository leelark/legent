package com.legent.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.identity.domain.Account;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.AccountRoleBinding;
import com.legent.identity.domain.Tenant;
import com.legent.identity.domain.User;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.AccountRepository;
import com.legent.identity.repository.AccountRoleBindingRepository;
import com.legent.identity.repository.FederationJdbcRepository;
import com.legent.identity.repository.TenantRepository;
import com.legent.identity.repository.UserRepository;
import com.legent.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

@Service
@RequiredArgsConstructor
public class FederatedIdentityService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_WORKSPACE_ID = "workspace-default";

    private final FederationJdbcRepository repository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountMembershipRepository accountMembershipRepository;
    private final AccountRoleBindingRepository accountRoleBindingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${legent.identity.federation.sp-entity-id:legent-email-studio}")
    private String serviceProviderEntityId;

    @Transactional
    public Map<String, Object> upsertProvider(String tenantId, FederationDto.ProviderRequest request) {
        String protocol = normalize(request.getProtocol());
        if (!List.of("OIDC", "SAML").contains(protocol)) {
            throw new IllegalArgumentException("protocol must be OIDC or SAML");
        }
        validateProviderRequest(protocol, request);
        Map<String, Object> values = baseValues(tenantId);
        values.put("provider_key", request.getProviderKey().trim());
        values.put("display_name", request.getDisplayName().trim());
        values.put("protocol", protocol);
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("issuer", blankToNull(request.getIssuer()));
        values.put("client_id", blankToNull(request.getClientId()));
        values.put("client_secret_ref", blankToNull(request.getClientSecretRef()));
        values.put("authorization_endpoint", blankToNull(request.getAuthorizationEndpoint()));
        values.put("token_endpoint", blankToNull(request.getTokenEndpoint()));
        values.put("userinfo_endpoint", blankToNull(request.getUserInfoEndpoint()));
        values.put("jwks_url", blankToNull(request.getJwksUrl()));
        values.put("redirect_uri", blankToNull(request.getRedirectUri()));
        values.put("scopes", toJson(request.getScopes() == null ? List.of("openid", "email", "profile") : request.getScopes()));
        values.put("entity_id", blankToNull(request.getEntityId()));
        values.put("sso_url", blankToNull(request.getSsoUrl()));
        values.put("audience", blankToNull(request.getAudience()));
        values.put("signing_certificate", blankToNull(request.getSigningCertificate()));
        values.put("jit_provisioning_enabled", request.getJitProvisioningEnabled() == null || request.getJitProvisioningEnabled());
        values.put("scim_enabled", request.getScimEnabled() != null && request.getScimEnabled());
        values.put("default_workspace_id", blankToNull(request.getDefaultWorkspaceId()));
        values.put("default_role_keys", toJson(request.getDefaultRoleKeys() == null ? List.of("USER") : request.getDefaultRoleKeys()));
        values.put("attribute_mapping", toJson(request.getAttributeMapping()));
        values.put("metadata", toJson(request.getMetadata()));

        Map<String, Object> existing = findProviderByKey(tenantId, request.getProviderKey()).orElse(null);
        Map<String, Object> saved;
        if (existing == null) {
            saved = repository.insert("federated_identity_providers", values,
                    List.of("scopes", "default_role_keys", "attribute_mapping", "metadata"));
        } else {
            Map<String, Object> updates = new LinkedHashMap<>(values);
            updates.remove("id");
            updates.remove("tenant_id");
            updates.remove("created_at");
            updates.remove("created_by");
            updates.remove("deleted_at");
            updates.remove("version");
            saved = repository.updateById("federated_identity_providers", String.valueOf(existing.get("id")), tenantId, updates,
                    List.of("scopes", "default_role_keys", "attribute_mapping", "metadata"));
        }
        return redactProvider(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProviders(String tenantId) {
        return repository.queryForList("""
                SELECT * FROM federated_identity_providers
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", tenantId)).stream().map(this::redactProvider).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProvider(String tenantId, String providerKey) {
        return findProviderByKey(tenantId, providerKey)
                .map(this::redactProvider)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerKey));
    }

    @Transactional
    public Map<String, Object> createScimToken(String tenantId, FederationDto.ScimTokenRequest request) {
        Map<String, Object> provider = findProviderById(tenantId, request.getProviderId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + request.getProviderId()));
        if (!Boolean.TRUE.equals(provider.get("scim_enabled"))) {
            throw new IllegalArgumentException("SCIM is not enabled for provider");
        }
        String rawToken = "legent_scim_" + randomUrlToken(32);
        Map<String, Object> values = baseValues(tenantId);
        values.put("provider_id", request.getProviderId());
        values.put("label", request.getLabel().trim());
        values.put("token_hash", sha256(rawToken));
        values.put("scopes", toJson(request.getScopes() == null ? List.of("scim:users", "scim:groups") : request.getScopes()));
        values.put("status", "ACTIVE");
        values.put("expires_at", request.getExpiresAt());
        values.put("last_used_at", null);
        Map<String, Object> saved = repository.insert("federation_scim_tokens", values, List.of("scopes"));
        saved.remove("token_hash");
        saved.put("token", rawToken);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listScimTokens(String tenantId) {
        return repository.queryForList("""
                SELECT id, tenant_id, provider_id, label, scopes, status, expires_at, last_used_at, created_at, updated_at, created_by, version
                FROM federation_scim_tokens
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", tenantId));
    }

    @Transactional
    public Map<String, Object> startLogin(String tenantId, String providerKey, String redirectAfter) {
        Map<String, Object> provider = activeProvider(tenantId, providerKey);
        String protocol = String.valueOf(provider.get("protocol"));
        return "OIDC".equalsIgnoreCase(protocol)
                ? startOidc(provider, redirectAfter)
                : startSaml(provider, redirectAfter);
    }

    @Transactional
    public FederatedLoginResult handleOidcCallback(String tenantId, String providerKey, String state, String code) {
        Map<String, Object> provider = activeProvider(tenantId, providerKey);
        Map<String, Object> loginState = consumeState(tenantId, provider, state, "OIDC");
        String idToken = exchangeCodeForIdToken(provider, code, String.valueOf(loginState.get("code_verifier")));
        Jwt jwt = decodeAndValidateIdToken(provider, idToken, String.valueOf(loginState.get("nonce")));
        Map<String, Object> claims = new LinkedHashMap<>(jwt.getClaims());
        ProvisionedPrincipal principal = provisionPrincipal(provider, claims);
        return issueResult(principal, asString(loginState.get("redirect_after")));
    }

    @Transactional
    public FederatedLoginResult handleSamlAcs(String tenantId, String providerKey, String samlResponse, String relayState) {
        Map<String, Object> provider = activeProvider(tenantId, providerKey);
        Map<String, Object> loginState = consumeState(tenantId, provider, relayState, "SAML");
        Document document = parseSamlDocument(samlResponse);
        validateSamlResponse(provider, loginState, document);
        Map<String, Object> attributes = extractSamlAttributes(document);
        ProvisionedPrincipal principal = provisionPrincipal(provider, attributes);
        return issueResult(principal, asString(loginState.get("redirect_after")));
    }

    @Transactional(readOnly = true)
    public String samlMetadata(String tenantId, String providerKey, String acsUrl) {
        Map<String, Object> provider = activeProvider(tenantId, providerKey);
        if (!"SAML".equalsIgnoreCase(String.valueOf(provider.get("protocol")))) {
            throw new IllegalArgumentException("Provider is not SAML");
        }
        String entityId = defaultValue(asString(provider.get("entity_id")), serviceProviderEntityId);
        String acs = defaultValue(acsUrl, asString(provider.get("redirect_uri")));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
                  <md:SPSSODescriptor AuthnRequestsSigned="false" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s" index="0" isDefault="true"/>
                  </md:SPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(xmlEscape(entityId), xmlEscape(acs));
    }

    ProvisionedPrincipal provisionPrincipal(Map<String, Object> provider, Map<String, Object> attributes) {
        String tenantId = asString(provider.get("tenant_id"));
        if (!Boolean.TRUE.equals(provider.get("jit_provisioning_enabled"))) {
            throw new IllegalStateException("JIT provisioning disabled for provider");
        }
        ensureTenantExists(tenantId);
        Map<String, Object> mapping = readMap(provider.get("attribute_mapping"));
        String email = firstNonBlank(
                mappedAttribute(attributes, mapping, "email"),
                stringAttribute(attributes, "email"),
                stringAttribute(attributes, "mail"),
                stringAttribute(attributes, "userName"),
                stringAttribute(attributes, "NameID"));
        if (email == null) {
            throw new IllegalArgumentException("Federated assertion missing email/userName");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String externalId = firstNonBlank(
                mappedAttribute(attributes, mapping, "externalId"),
                stringAttribute(attributes, "sub"),
                stringAttribute(attributes, "externalId"),
                normalizedEmail);
        String firstName = firstNonBlank(mappedAttribute(attributes, mapping, "firstName"), stringAttribute(attributes, "given_name"), stringAttribute(attributes, "givenName"));
        String lastName = firstNonBlank(mappedAttribute(attributes, mapping, "lastName"), stringAttribute(attributes, "family_name"), stringAttribute(attributes, "familyName"));
        List<String> roles = roleKeys(provider, attributes);
        String workspaceId = defaultValue(asString(provider.get("default_workspace_id")), DEFAULT_WORKSPACE_ID);

        User user = userRepository.findByTenantIdAndIdentityProviderIdAndExternalId(tenantId, String.valueOf(provider.get("id")), externalId)
                .or(() -> userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail))
                .map(existing -> {
                    existing.setEmail(normalizedEmail);
                    existing.setFirstName(firstName);
                    existing.setLastName(lastName);
                    existing.setExternalId(externalId);
                    existing.setIdentityProviderId(String.valueOf(provider.get("id")));
                    existing.setRole(roles.get(0));
                    existing.setActive(true);
                    existing.setLastLoginAt(Instant.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .tenantId(tenantId)
                        .email(normalizedEmail)
                        .passwordHash(passwordEncoder.encode(randomUrlToken(32)))
                        .firstName(firstName)
                        .lastName(lastName)
                        .role(roles.get(0))
                        .isActive(true)
                        .externalId(externalId)
                        .identityProviderId(String.valueOf(provider.get("id")))
                        .lastLoginAt(Instant.now())
                        .build()));

        Account account = accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> {
                    Account created = new Account();
                    created.setEmail(normalizedEmail);
                    created.setPasswordHash(user.getPasswordHash());
                    created.setFirstName(firstName);
                    created.setLastName(lastName);
                    created.setStatus("ACTIVE");
                    return accountRepository.save(created);
                });
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setStatus("ACTIVE");
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        AccountMembership membership = accountMembershipRepository.findByAccountIdAndTenantId(account.getId(), tenantId)
                .orElseGet(() -> {
                    AccountMembership created = new AccountMembership();
                    created.setAccountId(account.getId());
                    created.setUserId(user.getId());
                    created.setTenantId(tenantId);
                    created.setWorkspaceId(workspaceId);
                    created.setStatus("ACTIVE");
                    created.setDefaultMembership(true);
                    created.setMetadata(Map.of("source", "federation", "providerId", provider.get("id")));
                    return accountMembershipRepository.save(created);
                });
        membership.setUserId(user.getId());
        membership.setWorkspaceId(defaultValue(membership.getWorkspaceId(), workspaceId));
        membership.setStatus("ACTIVE");
        accountMembershipRepository.save(membership);
        roles.forEach(role -> ensureRoleBinding(account.getId(), membership.getId(), role));
        return new ProvisionedPrincipal(user, account, membership, roles);
    }

    private Map<String, Object> startOidc(Map<String, Object> provider, String redirectAfter) {
        String state = randomUrlToken(32);
        String nonce = randomUrlToken(24);
        String codeVerifier = randomUrlToken(48);
        String challenge = base64Url(sha256Bytes(codeVerifier));
        saveLoginState(provider, "OIDC", null, state, nonce, codeVerifier, redirectAfter);
        String scope = String.join(" ", readStringList(provider.get("scopes")));
        String location = UriComponentsBuilder.fromUriString(String.valueOf(provider.get("authorization_endpoint")))
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.get("client_id"))
                .queryParam("redirect_uri", provider.get("redirect_uri"))
                .queryParam("scope", scope.isBlank() ? "openid email profile" : scope)
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        return Map.of("protocol", "OIDC", "state", state, "location", location, "expiresAt", Instant.now().plusSeconds(600).toString());
    }

    private Map<String, Object> startSaml(Map<String, Object> provider, String redirectAfter) {
        String relayState = randomUrlToken(32);
        String requestId = "_" + IdGenerator.newId();
        saveLoginState(provider, "SAML", requestId, relayState, null, null, redirectAfter);
        String issuer = defaultValue(asString(provider.get("entity_id")), serviceProviderEntityId);
        String acs = asString(provider.get("redirect_uri"));
        String authnRequest = """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="%s" Version="2.0" IssueInstant="%s" AssertionConsumerServiceURL="%s" Destination="%s">
                  <saml:Issuer xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">%s</saml:Issuer>
                  <samlp:NameIDPolicy Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress" AllowCreate="true"/>
                </samlp:AuthnRequest>
                """.formatted(requestId, Instant.now(), xmlEscape(acs), xmlEscape(String.valueOf(provider.get("sso_url"))), xmlEscape(issuer));
        String encoded = URLEncoder.encode(deflateAndBase64(authnRequest), StandardCharsets.UTF_8);
        String location = String.valueOf(provider.get("sso_url")) + (String.valueOf(provider.get("sso_url")).contains("?") ? "&" : "?")
                + "SAMLRequest=" + encoded + "&RelayState=" + URLEncoder.encode(relayState, StandardCharsets.UTF_8);
        return Map.of("protocol", "SAML", "state", relayState, "requestId", requestId, "location", location, "expiresAt", Instant.now().plusSeconds(600).toString());
    }

    private void saveLoginState(Map<String, Object> provider, String protocol, String requestId, String state, String nonce, String codeVerifier, String redirectAfter) {
        Map<String, Object> values = baseValues(asString(provider.get("tenant_id")));
        values.put("provider_id", provider.get("id"));
        values.put("protocol", protocol);
        values.put("request_id", requestId);
        values.put("state", state);
        values.put("nonce", nonce);
        values.put("code_verifier", codeVerifier);
        values.put("redirect_after", safeRedirectAfter(redirectAfter));
        values.put("expires_at", Instant.now().plusSeconds(600));
        values.put("consumed_at", null);
        repository.insert("federation_login_states", values, List.of());
    }

    private Map<String, Object> consumeState(String tenantId, Map<String, Object> provider, String state, String protocol) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Federation state is required");
        }
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM federation_login_states
                WHERE tenant_id = :tenantId
                  AND provider_id = :providerId
                  AND state = :state
                  AND protocol = :protocol
                  AND consumed_at IS NULL
                  AND expires_at > NOW()
                  AND deleted_at IS NULL
                LIMIT 1
                """, Map.of("tenantId", tenantId, "providerId", provider.get("id"), "state", state, "protocol", protocol));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Federation state is invalid or expired");
        }
        Map<String, Object> row = rows.get(0);
        repository.updateById("federation_login_states", String.valueOf(row.get("id")), tenantId,
                Map.of("consumed_at", Instant.now()), List.of());
        return row;
    }

    private String exchangeCodeForIdToken(Map<String, Object> provider, String code, String codeVerifier) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("OIDC code is required");
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", asString(provider.get("redirect_uri")));
        form.add("client_id", asString(provider.get("client_id")));
        form.add("code_verifier", codeVerifier);
        resolveClientSecret(provider).ifPresent(secret -> form.add("client_secret", secret));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                String.valueOf(provider.get("token_endpoint")),
                new HttpEntity<>(form, headers),
                Map.class);
        if (response == null || response.get("id_token") == null) {
            throw new IllegalArgumentException("OIDC token endpoint did not return id_token");
        }
        return String.valueOf(response.get("id_token"));
    }

    private Jwt decodeAndValidateIdToken(Map<String, Object> provider, String idToken, String expectedNonce) {
        Jwt jwt = NimbusJwtDecoder.withJwkSetUri(String.valueOf(provider.get("jwks_url"))).build().decode(idToken);
        if (provider.get("issuer") != null && !String.valueOf(provider.get("issuer")).equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())) {
            throw new IllegalArgumentException("OIDC issuer mismatch");
        }
        String clientId = asString(provider.get("client_id"));
        if (clientId != null && !jwt.getAudience().contains(clientId)) {
            throw new IllegalArgumentException("OIDC audience mismatch");
        }
        if (expectedNonce != null && !expectedNonce.equals(jwt.getClaimAsString("nonce"))) {
            throw new IllegalArgumentException("OIDC nonce mismatch");
        }
        if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("OIDC id_token expired");
        }
        return jwt;
    }

    private void validateSamlResponse(Map<String, Object> provider, Map<String, Object> loginState, Document document) {
        requireSingleAssertion(document);
        validateSamlSignature(provider, document);
        Element root = document.getDocumentElement();
        if (root.hasAttribute("InResponseTo") && !Objects.equals(root.getAttribute("InResponseTo"), asString(loginState.get("request_id")))) {
            throw new IllegalArgumentException("SAML InResponseTo mismatch");
        }
        if (provider.get("issuer") != null) {
            String issuer = firstElementText(document, "urn:oasis:names:tc:SAML:2.0:assertion", "Issuer");
            if (!String.valueOf(provider.get("issuer")).equals(issuer)) {
                throw new IllegalArgumentException("SAML issuer mismatch");
            }
        }
        String notOnOrAfter = firstAttribute(document, "SubjectConfirmationData", "NotOnOrAfter");
        if (notOnOrAfter != null && Instant.parse(notOnOrAfter).isBefore(Instant.now())) {
            throw new IllegalArgumentException("SAML assertion expired");
        }
        String audience = firstElementText(document, "urn:oasis:names:tc:SAML:2.0:assertion", "Audience");
        String expectedAudience = firstNonBlank(asString(provider.get("audience")), asString(provider.get("entity_id")), serviceProviderEntityId);
        if (audience != null && expectedAudience != null && !expectedAudience.equals(audience)) {
            throw new IllegalArgumentException("SAML audience mismatch");
        }
    }

    private void validateSamlSignature(Map<String, Object> provider, Document document) {
        String certPem = asString(provider.get("signing_certificate"));
        if (certPem == null || certPem.isBlank()) {
            throw new IllegalArgumentException("SAML signing certificate is required");
        }
        try {
            NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatures.getLength() == 0) {
                throw new IllegalArgumentException("SAML signature is required");
            }
            X509Certificate certificate = parseCertificate(certPem);
            DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signatures.item(0));
            context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            if (!signature.validate(context)) {
                throw new IllegalArgumentException("SAML signature validation failed");
            }
            if (!signatureCoversResponseOrAssertion(signature, document)) {
                throw new IllegalArgumentException("SAML signature must cover Response or Assertion");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to validate SAML signature", e);
        }
    }

    private void requireSingleAssertion(Document document) {
        NodeList assertions = document.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion");
        if (assertions.getLength() != 1) {
            throw new IllegalArgumentException("SAML response must contain exactly one Assertion");
        }
    }

    private boolean signatureCoversResponseOrAssertion(XMLSignature signature, Document document) {
        for (Object referenceObject : signature.getSignedInfo().getReferences()) {
            Reference reference = (Reference) referenceObject;
            String uri = reference.getURI();
            if (uri == null || uri.isBlank() || !uri.startsWith("#")) {
                continue;
            }
            Element signed = document.getElementById(uri.substring(1));
            if (signed == null) {
                continue;
            }
            String localName = signed.getLocalName();
            if ("Response".equals(localName) || "Assertion".equals(localName)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> extractSamlAttributes(Document document) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String nameId = firstElementText(document, "urn:oasis:names:tc:SAML:2.0:assertion", "NameID");
        if (nameId != null) {
            attributes.put("NameID", nameId);
            attributes.put("email", nameId);
        }
        NodeList nodes = document.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element attr = (Element) nodes.item(i);
            String name = firstNonBlank(attr.getAttribute("Name"), attr.getAttribute("FriendlyName"));
            NodeList values = attr.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
            if (name != null && values.getLength() > 0) {
                attributes.put(name, values.item(0).getTextContent());
            }
        }
        return attributes;
    }

    private FederatedLoginResult issueResult(ProvisionedPrincipal principal, String redirectAfter) {
        String token = tokenProvider.generateToken(
                principal.user().getId(),
                principal.user().getTenantId(),
                defaultValue(principal.membership().getWorkspaceId(), DEFAULT_WORKSPACE_ID),
                null,
                Map.of(
                        "roles", principal.roles(),
                        "email", principal.user().getEmail(),
                        "accountId", principal.account().getId(),
                        "federated", true
                ));
        return new FederatedLoginResult(
                token,
                principal.user().getId(),
                principal.user().getTenantId(),
                defaultValue(principal.membership().getWorkspaceId(), DEFAULT_WORKSPACE_ID),
                principal.roles(),
                redirectAfter == null ? "/app" : redirectAfter);
    }

    private Optional<Map<String, Object>> findProviderByKey(String tenantId, String providerKey) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM federated_identity_providers
                WHERE tenant_id = :tenantId AND provider_key = :providerKey AND deleted_at IS NULL
                LIMIT 1
                """, Map.of("tenantId", tenantId, "providerKey", providerKey));
        return rows.stream().findFirst();
    }

    private Optional<Map<String, Object>> findProviderById(String tenantId, String providerId) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM federated_identity_providers
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                LIMIT 1
                """, Map.of("tenantId", tenantId, "id", providerId));
        return rows.stream().findFirst();
    }

    private Map<String, Object> activeProvider(String tenantId, String providerKey) {
        Map<String, Object> provider = findProviderByKey(tenantId, providerKey)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerKey));
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(provider.get("status")))) {
            throw new IllegalStateException("Provider is not active");
        }
        return provider;
    }

    private void validateProviderRequest(String protocol, FederationDto.ProviderRequest request) {
        if ("OIDC".equals(protocol)) {
            require(request.getIssuer(), "issuer");
            require(request.getClientId(), "clientId");
            require(request.getAuthorizationEndpoint(), "authorizationEndpoint");
            require(request.getTokenEndpoint(), "tokenEndpoint");
            require(request.getJwksUrl(), "jwksUrl");
            require(request.getRedirectUri(), "redirectUri");
            requireTrustedHttpUrl(request.getAuthorizationEndpoint(), "authorizationEndpoint");
            requireTrustedHttpUrl(request.getTokenEndpoint(), "tokenEndpoint");
            requireTrustedHttpUrl(request.getJwksUrl(), "jwksUrl");
            requireTrustedHttpUrl(request.getRedirectUri(), "redirectUri");
        } else {
            require(request.getSsoUrl(), "ssoUrl");
            require(request.getRedirectUri(), "redirectUri");
            require(request.getSigningCertificate(), "signingCertificate");
            requireTrustedHttpUrl(request.getSsoUrl(), "ssoUrl");
            requireTrustedHttpUrl(request.getRedirectUri(), "redirectUri");
        }
    }

    private void ensureTenantExists(String tenantId) {
        if (tenantRepository.existsById(tenantId)) {
            return;
        }
        tenantRepository.save(Tenant.builder()
                .id(tenantId)
                .name("Federated Tenant " + tenantId)
                .status("ACTIVE")
                .settings("{}")
                .build());
    }

    private void ensureRoleBinding(String accountId, String membershipId, String role) {
        String roleKey = role == null || role.isBlank() ? "USER" : role.trim().toUpperCase(Locale.ROOT);
        boolean exists = accountRoleBindingRepository.findByMembershipId(membershipId).stream()
                .anyMatch(binding -> roleKey.equalsIgnoreCase(binding.getRoleKey()));
        if (exists) {
            return;
        }
        AccountRoleBinding binding = new AccountRoleBinding();
        binding.setAccountId(accountId);
        binding.setMembershipId(membershipId);
        binding.setRoleKey(roleKey);
        binding.setScopeType("TENANT");
        accountRoleBindingRepository.save(binding);
    }

    private List<String> roleKeys(Map<String, Object> provider, Map<String, Object> attributes) {
        List<String> roles = readStringList(provider.get("default_role_keys"));
        Object groups = attributes.get("groups");
        if (groups instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String role = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
                if (!role.isBlank() && !roles.contains(role)) {
                    roles = new ArrayList<>(roles);
                    roles.add(role);
                }
            }
        }
        return roles.isEmpty() ? List.of("USER") : roles.stream().map(this::normalize).distinct().toList();
    }

    private Map<String, Object> redactProvider(Map<String, Object> provider) {
        Map<String, Object> redacted = new LinkedHashMap<>(provider);
        redacted.remove("signing_certificate");
        redacted.put("hasSigningCertificate", provider.get("signing_certificate") != null);
        redacted.put("hasClientSecretRef", provider.get("client_secret_ref") != null);
        return redacted;
    }

    private Optional<String> resolveClientSecret(Map<String, Object> provider) {
        String ref = asString(provider.get("client_secret_ref"));
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        if (ref.startsWith("env:")) {
            String envKey = ref.substring(4);
            String value = System.getenv(envKey);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("OIDC client secret env reference is not available: " + envKey);
            }
            return Optional.of(value);
        }
        throw new IllegalStateException("Unsupported OIDC client secret reference: " + ref);
    }

    private Document parseSamlDocument(String samlResponse) {
        try {
            byte[] xml = Base64.getDecoder().decode(samlResponse);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            markIdAttributes(document);
            return document;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid SAMLResponse", e);
        }
    }

    private X509Certificate parseCertificate(String certPem) throws Exception {
        String normalized = certPem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(normalized);
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(bytes));
    }

    private void markIdAttributes(Document document) {
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element.hasAttribute("ID")) {
                element.setIdAttribute("ID", true);
            }
            if (element.hasAttribute("Id")) {
                element.setIdAttribute("Id", true);
            }
            if (element.hasAttribute("AssertionID")) {
                element.setIdAttribute("AssertionID", true);
            }
        }
    }

    private String firstElementText(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private String firstAttribute(Document document, String localName, String attribute) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Element element = (Element) nodes.item(0);
        return element.hasAttribute(attribute) ? element.getAttribute(attribute) : null;
    }

    private String mappedAttribute(Map<String, Object> attributes, Map<String, Object> mapping, String field) {
        Object key = mapping.get(field);
        return key == null ? null : stringAttribute(attributes, String.valueOf(key));
    }

    private String stringAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> baseValues(String tenantId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenantId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", "SYSTEM");
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private String deflateAndBase64(String value) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFLATED, true);
            try (DeflaterOutputStream deflaterOutput = new DeflaterOutputStream(output, deflater)) {
                deflaterOutput.write(value.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encode SAML request", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize federation payload", e);
        }
    }

    private String randomUrlToken(int bytes) {
        byte[] buffer = new byte[bytes];
        SECURE_RANDOM.nextBytes(buffer);
        return base64Url(buffer);
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash value", e);
        }
    }

    private String sha256(String value) {
        byte[] hash = sha256Bytes(value);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requireTrustedHttpUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || (!"https".equalsIgnoreCase(scheme) && !isLocalHttpUrl(scheme, host))) {
                throw new IllegalArgumentException(field + " must be https, or http only for localhost development");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a valid URL", e);
        }
    }

    private boolean isLocalHttpUrl(String scheme, String host) {
        return "http".equalsIgnoreCase(scheme)
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
    }

    private String safeRedirectAfter(String redirectAfter) {
        String value = blankToNull(redirectAfter);
        if (value == null) {
            return "/app";
        }
        return value.startsWith("/") && !value.startsWith("//") && !value.contains("\\") ? value : "/app";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String xmlEscape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record FederatedLoginResult(
            String token,
            String userId,
            String tenantId,
            String workspaceId,
            List<String> roles,
            String redirectAfter) {
    }

    record ProvisionedPrincipal(User user, Account account, AccountMembership membership, List<String> roles) {
    }
}
