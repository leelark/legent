package com.legent.identity.service;

import com.legent.common.util.IdGenerator;
import com.legent.identity.domain.*;
import com.legent.identity.dto.AuthBridgeDto;
import com.legent.identity.dto.SignupRequest;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.identity.repository.*;
import com.legent.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountMembershipRepository accountMembershipRepository;
    private final AccountRoleBindingRepository accountRoleBindingRepository;
    private final AuthInvitationRepository authInvitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final IdentityEventPublisher eventPublisher;

    @Transactional
    public String login(String email, String password, String tenantId) {
        if (email == null || password == null || tenantId == null
                || email.isBlank() || password.isBlank() || tenantId.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String normalizedTenantId = tenantId.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByTenantIdAndEmailIgnoreCase(normalizedTenantId, normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (accountRepository == null || accountMembershipRepository == null || accountRoleBindingRepository == null) {
            return tokenProvider.generateToken(
                    user.getId(),
                    user.getTenantId(),
                    Map.of(
                            "roles", Collections.singletonList(user.getRole()),
                            "email", user.getEmail()
                    ));
        }

        Account account = ensureAccount(user);
        AccountMembership membership = ensureMembership(account, user, normalizedTenantId, null);
        List<String> roles = resolveMembershipRoles(account.getId(), membership.getId(), user.getRole());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        return tokenProvider.generateToken(
                user.getId(),
                user.getTenantId(),
                membership.getWorkspaceId(),
                null,
                Map.of(
                        "roles", roles,
                        "email", user.getEmail(),
                        "accountId", account.getId()
                ));
    }

    @Transactional
    public String signup(SignupRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            String companyName = request.getCompanyName();
            if (companyName == null || companyName.isBlank()) {
                throw new IllegalArgumentException("Company name is required when slug is not provided");
            }
            slug = companyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-");
        }

        String tenantId = IdGenerator.newId();

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name(request.getCompanyName())
                .status("ACTIVE")
                .settings("{}")
                .build();
        tenantRepository.save(tenant);

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.getEmail().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role("ADMIN")
                .isActive(true)
                .build();

        User savedUser = userRepository.save(Objects.requireNonNull(user));
        Account account = ensureAccount(savedUser);
        AccountMembership membership = ensureMembership(account, savedUser, tenantId, null);
        ensureRoleBinding(account.getId(), membership.getId(), "ADMIN");

        eventPublisher.publishSignup(tenantId, savedUser.getId(), savedUser.getEmail(), request.getCompanyName(), slug);
        log.info("User signed up: email={}, tenantId={}", savedUser.getEmail(), tenantId);

        return tokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getTenantId(),
                membership.getWorkspaceId(),
                null,
                Map.of(
                        "roles", resolveMembershipRoles(account.getId(), membership.getId(), "ADMIN"),
                        "email", savedUser.getEmail(),
                        "accountId", account.getId()
                ));
    }

    @Transactional(readOnly = true)
    public List<String> getUserRoles(String tenantId, String userId) {
        if (accountMembershipRepository == null || accountRoleBindingRepository == null) {
            return userRepository.findByTenantIdAndId(tenantId, userId)
                    .filter(User::isActive)
                    .map(User::getRole)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }

        Optional<AccountMembership> membership = accountMembershipRepository.findByUserIdAndTenantId(userId, tenantId);
        if (membership.isPresent()) {
            String accountId = membership.get().getAccountId();
            List<String> roles = resolveMembershipRoles(accountId, membership.get().getId(), null);
            if (!roles.isEmpty()) {
                return roles;
            }
        }

        return userRepository.findByTenantIdAndId(tenantId, userId)
                .filter(User::isActive)
                .map(User::getRole)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Transactional
    public AuthInvitation createInvitation(String tenantId, String invitedByUserId, AuthBridgeDto.InvitationRequest request) {
        AuthInvitation invitation = new AuthInvitation();
        invitation.setTenantId(tenantId);
        invitation.setWorkspaceId(blankToNull(request.getWorkspaceId()));
        invitation.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
        invitation.setRoleKeys(request.getRoleKeys() == null || request.getRoleKeys().isEmpty()
                ? List.of("USER")
                : request.getRoleKeys());
        invitation.setInvitedByUserId(invitedByUserId);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(request.getExpiresAt());
        invitation.setMetadata(request.getMetadata());
        return authInvitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public List<AuthInvitation> listInvitations(String tenantId) {
        return authInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public String acceptInvitation(AuthBridgeDto.InvitationAcceptRequest request) {
        AuthInvitation invitation = authInvitationRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalStateException("Invitation is not active");
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Invitation has expired");
        }

        Account account = accountRepository.findByEmailIgnoreCase(invitation.getEmail())
                .orElseGet(() -> {
                    Account created = new Account();
                    created.setEmail(invitation.getEmail());
                    String password = request.getPassword() != null && !request.getPassword().isBlank()
                            ? request.getPassword()
                            : UUID.randomUUID().toString();
                    created.setPasswordHash(passwordEncoder.encode(password));
                    created.setFirstName(request.getFirstName());
                    created.setLastName(request.getLastName());
                    created.setStatus("ACTIVE");
                    return accountRepository.save(created);
                });

        User user = userRepository.findByTenantIdAndEmailIgnoreCase(invitation.getTenantId(), invitation.getEmail())
                .orElseGet(() -> {
                    User created = User.builder()
                            .tenantId(invitation.getTenantId())
                            .email(invitation.getEmail())
                            .passwordHash(account.getPasswordHash())
                            .firstName(account.getFirstName())
                            .lastName(account.getLastName())
                            .role("USER")
                            .isActive(true)
                            .build();
                    return userRepository.save(created);
                });

        AccountMembership membership = ensureMembership(
                account,
                user,
                invitation.getTenantId(),
                invitation.getWorkspaceId());

        List<String> roles = invitation.getRoleKeys() == null || invitation.getRoleKeys().isEmpty()
                ? List.of("USER")
                : invitation.getRoleKeys();
        for (String role : roles) {
            ensureRoleBinding(account.getId(), membership.getId(), role);
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(Instant.now());
        authInvitationRepository.save(invitation);

        return tokenProvider.generateToken(
                user.getId(),
                user.getTenantId(),
                membership.getWorkspaceId(),
                null,
                Map.of(
                        "roles", resolveMembershipRoles(account.getId(), membership.getId(), user.getRole()),
                        "email", user.getEmail(),
                        "accountId", account.getId()
                ));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAccountContexts(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }
        String email = userOpt.get().getEmail();
        Optional<Account> accountOpt = accountRepository.findByEmailIgnoreCase(email);
        if (accountOpt.isEmpty()) {
            return List.of(Map.of(
                    "tenantId", userOpt.get().getTenantId(),
                    "workspaceId", null,
                    "roles", List.of(userOpt.get().getRole()),
                    "status", userOpt.get().isActive() ? "ACTIVE" : "INACTIVE"
            ));
        }

        Account account = accountOpt.get();
        List<AccountMembership> memberships = accountMembershipRepository.findByAccountIdAndStatus(account.getId(), "ACTIVE");
        List<Map<String, Object>> contexts = new ArrayList<>();
        for (AccountMembership membership : memberships) {
            List<String> roles = resolveMembershipRoles(account.getId(), membership.getId(), "USER");
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("tenantId", membership.getTenantId());
            ctx.put("workspaceId", membership.getWorkspaceId());
            ctx.put("roles", roles);
            ctx.put("status", membership.getStatus());
            ctx.put("default", membership.isDefaultMembership());
            contexts.add(ctx);
        }
        return contexts;
    }

    @Transactional
    public String switchContext(String userId, AuthBridgeDto.ContextSwitchRequest request) {
        User user = userRepository.findByTenantIdAndId(request.getTenantId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of tenant"));

        Optional<Account> accountOpt = accountRepository.findByEmailIgnoreCase(user.getEmail());
        if (accountOpt.isEmpty()) {
            return tokenProvider.generateToken(
                    userId,
                    request.getTenantId(),
                    request.getWorkspaceId(),
                    request.getEnvironmentId(),
                    Map.of("roles", List.of(user.getRole()))
            );
        }

        Account account = accountOpt.get();
        AccountMembership membership = accountMembershipRepository
                .findByAccountIdAndTenantId(account.getId(), request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Membership not found for target tenant"));

        List<String> roles = resolveMembershipRoles(account.getId(), membership.getId(), user.getRole());
        return tokenProvider.generateToken(
                userId,
                request.getTenantId(),
                blankToNull(request.getWorkspaceId()),
                blankToNull(request.getEnvironmentId()),
                Map.of(
                        "roles", roles,
                        "email", user.getEmail(),
                        "accountId", account.getId()
                )
        );
    }

    @Transactional
    public String exchangeDelegationToken(String userId, String tenantId, AuthBridgeDto.DelegationRequest request) {
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<String> delegatedRoles = request.getPermissions() == null || request.getPermissions().isEmpty()
                ? List.of("VIEWER")
                : request.getPermissions();

        Map<String, Object> extraClaims = new LinkedHashMap<>();
        extraClaims.put("roles", delegatedRoles);
        extraClaims.put("delegatedBy", userId);
        extraClaims.put("delegatedUserId", request.getDelegatedUserId());
        if (request.getExpiresAt() != null) {
            extraClaims.put("delegationExpiresAt", request.getExpiresAt().toString());
        }

        return tokenProvider.generateToken(
                request.getDelegatedUserId(),
                tenantId,
                blankToNull(request.getWorkspaceId()),
                null,
                extraClaims
        );
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveDefaultTenantId(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (accountRepository == null || accountMembershipRepository == null) {
            return userRepository.findFirstByEmailIgnoreCase(normalized).map(User::getTenantId);
        }

        Optional<Account> accountOpt = accountRepository.findByEmailIgnoreCase(normalized);
        if (accountOpt.isPresent()) {
            return accountMembershipRepository.findByAccountIdAndStatus(accountOpt.get().getId(), "ACTIVE").stream()
                    .sorted(Comparator.comparing(AccountMembership::isDefaultMembership).reversed())
                    .map(AccountMembership::getTenantId)
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        return userRepository.findFirstByEmailIgnoreCase(normalized)
                .map(User::getTenantId);
    }

    private Account ensureAccount(User user) {
        if (accountRepository == null) {
            Account account = new Account();
            account.setId(IdGenerator.newId());
            account.setEmail(user.getEmail());
            account.setPasswordHash(user.getPasswordHash());
            account.setFirstName(user.getFirstName());
            account.setLastName(user.getLastName());
            account.setStatus(user.isActive() ? "ACTIVE" : "INACTIVE");
            return account;
        }
        return accountRepository.findByEmailIgnoreCase(user.getEmail())
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setEmail(user.getEmail());
                    account.setPasswordHash(user.getPasswordHash());
                    account.setFirstName(user.getFirstName());
                    account.setLastName(user.getLastName());
                    account.setStatus(user.isActive() ? "ACTIVE" : "INACTIVE");
                    return accountRepository.save(account);
                });
    }

    private AccountMembership ensureMembership(Account account, User user, String tenantId, String workspaceId) {
        if (accountMembershipRepository == null) {
            AccountMembership membership = new AccountMembership();
            membership.setId(IdGenerator.newId());
            membership.setAccountId(account.getId());
            membership.setUserId(user.getId());
            membership.setTenantId(tenantId);
            membership.setWorkspaceId(blankToNull(workspaceId));
            membership.setStatus("ACTIVE");
            membership.setDefaultMembership(true);
            return membership;
        }
        return accountMembershipRepository.findByAccountIdAndTenantId(account.getId(), tenantId)
                .orElseGet(() -> {
                    AccountMembership membership = new AccountMembership();
                    membership.setAccountId(account.getId());
                    membership.setUserId(user.getId());
                    membership.setTenantId(tenantId);
                    membership.setWorkspaceId(blankToNull(workspaceId));
                    membership.setStatus("ACTIVE");
                    membership.setDefaultMembership(true);
                    membership.setMetadata(Map.of("legacyUserId", user.getId()));
                    return accountMembershipRepository.save(membership);
                });
    }

    private void ensureRoleBinding(String accountId, String membershipId, String role) {
        if (accountRoleBindingRepository == null) {
            return;
        }
        boolean exists = accountRoleBindingRepository.findByMembershipId(membershipId).stream()
                .anyMatch(binding -> role.equalsIgnoreCase(binding.getRoleKey()));
        if (exists) {
            return;
        }

        AccountRoleBinding binding = new AccountRoleBinding();
        binding.setAccountId(accountId);
        binding.setMembershipId(membershipId);
        binding.setRoleKey(role.toUpperCase(Locale.ROOT));
        binding.setScopeType("TENANT");
        binding.setScopeId(null);
        accountRoleBindingRepository.save(binding);
    }

    private List<String> resolveMembershipRoles(String accountId, String membershipId, String fallbackRole) {
        if (accountRoleBindingRepository == null) {
            if (fallbackRole != null && !fallbackRole.isBlank()) {
                return List.of(fallbackRole.toUpperCase(Locale.ROOT));
            }
            return List.of("USER");
        }

        List<String> roles = accountRoleBindingRepository.findByMembershipId(membershipId).stream()
                .filter(binding -> binding.getEffectiveUntil() == null || binding.getEffectiveUntil().isAfter(Instant.now()))
                .map(AccountRoleBinding::getRoleKey)
                .filter(Objects::nonNull)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (!roles.isEmpty()) {
            return roles;
        }
        if (fallbackRole != null && !fallbackRole.isBlank()) {
            return List.of(fallbackRole.toUpperCase(Locale.ROOT));
        }
        return List.of("USER");
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
