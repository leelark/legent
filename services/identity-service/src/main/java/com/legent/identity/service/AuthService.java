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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String DEFAULT_WORKSPACE_ID = "workspace-default";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";
    private static final String ROLE_SECURITY_ADMIN = "SECURITY_ADMIN";
    private static final String ROLE_WORKSPACE_OWNER = "WORKSPACE_OWNER";
    private static final String ROLE_CAMPAIGN_MANAGER = "CAMPAIGN_MANAGER";
    private static final String ROLE_DELIVERY_OPERATOR = "DELIVERY_OPERATOR";
    private static final String ROLE_ANALYST = "ANALYST";
    private static final String ROLE_VIEWER = "VIEWER";
    private static final String ROLE_USER = "USER";
    private static final Duration DEFAULT_INVITATION_TTL = Duration.ofDays(7);
    private static final Duration MAX_INVITATION_TTL = Duration.ofDays(7);
    private static final Set<String> ALL_ROLE_KEYS = Set.of(
            ROLE_ADMIN,
            ROLE_PLATFORM_ADMIN,
            ROLE_ORG_ADMIN,
            ROLE_SECURITY_ADMIN,
            ROLE_WORKSPACE_OWNER,
            ROLE_CAMPAIGN_MANAGER,
            ROLE_DELIVERY_OPERATOR,
            ROLE_ANALYST,
            ROLE_VIEWER,
            ROLE_USER);
    private static final Set<String> USER_MANAGEMENT_ROLES = Set.of(
            ROLE_ADMIN,
            ROLE_PLATFORM_ADMIN,
            ROLE_ORG_ADMIN,
            ROLE_SECURITY_ADMIN);
    private static final Map<String, Set<String>> ROLE_GRANT_MATRIX = Map.ofEntries(
            Map.entry(ROLE_ADMIN, ALL_ROLE_KEYS),
            Map.entry(ROLE_PLATFORM_ADMIN, Set.of(
                    ROLE_PLATFORM_ADMIN,
                    ROLE_ORG_ADMIN,
                    ROLE_SECURITY_ADMIN,
                    ROLE_WORKSPACE_OWNER,
                    ROLE_CAMPAIGN_MANAGER,
                    ROLE_DELIVERY_OPERATOR,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_ORG_ADMIN, Set.of(
                    ROLE_ORG_ADMIN,
                    ROLE_WORKSPACE_OWNER,
                    ROLE_CAMPAIGN_MANAGER,
                    ROLE_DELIVERY_OPERATOR,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_SECURITY_ADMIN, Set.of(
                    ROLE_SECURITY_ADMIN,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_WORKSPACE_OWNER, Set.of(
                    ROLE_WORKSPACE_OWNER,
                    ROLE_CAMPAIGN_MANAGER,
                    ROLE_DELIVERY_OPERATOR,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_CAMPAIGN_MANAGER, Set.of(
                    ROLE_CAMPAIGN_MANAGER,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_DELIVERY_OPERATOR, Set.of(
                    ROLE_DELIVERY_OPERATOR,
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_ANALYST, Set.of(
                    ROLE_ANALYST,
                    ROLE_VIEWER,
                    ROLE_USER)),
            Map.entry(ROLE_VIEWER, Set.of(ROLE_VIEWER, ROLE_USER)),
            Map.entry(ROLE_USER, Set.of(ROLE_USER)));

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
                normalizeWorkspaceId(membership.getWorkspaceId()),
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
                normalizeWorkspaceId(membership.getWorkspaceId()),
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
        User inviter = userRepository.findByTenantIdAndId(tenantId, invitedByUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Inviter is not authorized for tenant"));
        MembershipRoles inviterMembership = resolveUserMembershipRoles(
                inviter,
                tenantId,
                request.getWorkspaceId(),
                "Inviter membership not found");
        List<String> requestedRoles = normalizeRoleKeys(request.getRoleKeys(), ROLE_USER);
        assertUserManagementAllowed(inviterMembership.roles(), "Inviter is not authorized to invite users");
        assertGrantAllowed(inviterMembership.roles(), requestedRoles, "Invitation roles exceed inviter roles");

        AuthInvitation invitation = new AuthInvitation();
        invitation.setTenantId(tenantId);
        invitation.setWorkspaceId(blankToNull(request.getWorkspaceId()));
        invitation.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
        invitation.setRoleKeys(requestedRoles);
        invitation.setInvitedByUserId(invitedByUserId);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(resolveInvitationExpiresAt(request.getExpiresAt()));
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

        List<String> roles = normalizeRoleKeys(invitation.getRoleKeys(), ROLE_USER);
        assertInvitationGrantStillAllowed(invitation, roles);

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

        for (String role : roles) {
            ensureRoleBinding(account.getId(), membership.getId(), role);
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(Instant.now());
        authInvitationRepository.save(invitation);

        return tokenProvider.generateToken(
                user.getId(),
                user.getTenantId(),
                normalizeWorkspaceId(membership.getWorkspaceId()),
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
                    "workspaceId", DEFAULT_WORKSPACE_ID,
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
            ctx.put("workspaceId", normalizeWorkspaceId(membership.getWorkspaceId()));
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
            if (blankToNull(request.getWorkspaceId()) != null
                    && !DEFAULT_WORKSPACE_ID.equals(normalizeWorkspaceId(request.getWorkspaceId()))) {
                throw new IllegalArgumentException("Membership not found for target workspace");
            }
            return tokenProvider.generateToken(
                    userId,
                    request.getTenantId(),
                    DEFAULT_WORKSPACE_ID,
                    null,
                    Map.of("roles", List.of(user.getRole()))
            );
        }

        Account account = accountOpt.get();
        AccountMembership membership = resolveActiveMembershipForContext(
                account.getId(),
                request.getTenantId(),
                request.getWorkspaceId());

        List<String> roles = resolveMembershipRoles(account.getId(), membership.getId(), user.getRole());
        return tokenProvider.generateToken(
                userId,
                request.getTenantId(),
                normalizeWorkspaceId(membership.getWorkspaceId()),
                null,
                Map.of(
                "roles", roles,
                "email", user.getEmail(),
                "accountId", account.getId()
                )
        );
    }

    @Transactional
    public String exchangeDelegationToken(String userId, String tenantId, AuthBridgeDto.DelegationRequest request) {
        User requester = userRepository.findByTenantIdAndId(tenantId, userId)
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Account requesterAccount = accountRepository.findByEmailIgnoreCase(requester.getEmail())
                .filter(this::isActiveAccount)
                .orElseThrow(() -> new IllegalArgumentException("Requester account not found"));
        AccountMembership requesterMembership = resolveActiveMembershipForContext(
                requesterAccount.getId(),
                tenantId,
                request.getWorkspaceId());
        if (!Objects.equals(requesterMembership.getUserId(), requester.getId())) {
            throw new IllegalArgumentException("Requester membership not found");
        }
        List<String> requesterRoles = resolveMembershipRoles(
                requesterAccount.getId(),
                requesterMembership.getId(),
                requester.getRole());
        assertUserManagementAllowed(requesterRoles, "Requester is not authorized to delegate users");
        User delegatedUser = userRepository.findByTenantIdAndId(tenantId, request.getDelegatedUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Delegated user not found"));
        Account delegatedAccount = accountRepository.findByEmailIgnoreCase(delegatedUser.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Delegated user account not found"));
        AccountMembership delegatedMembership = resolveActiveMembershipForContext(
                delegatedAccount.getId(),
                tenantId,
                request.getWorkspaceId());
        if (!Objects.equals(delegatedMembership.getUserId(), delegatedUser.getId())) {
            throw new IllegalArgumentException("Delegated user membership not found");
        }
        String workspaceId = delegatedMembership.getWorkspaceId();
        List<String> delegatedUserRoles = resolveMembershipRoles(
                delegatedAccount.getId(),
                delegatedMembership.getId(),
                delegatedUser.getRole());

        List<String> delegatedRoles = normalizeRoleKeys(request.getPermissions(), ROLE_VIEWER);
        assertGrantAllowed(requesterRoles, delegatedRoles, "Delegated roles exceed requester roles");
        assertGrantAllowed(delegatedUserRoles, delegatedRoles, "Delegated roles exceed delegated user roles");

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
                normalizeWorkspaceId(workspaceId),
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
        String normalizedWorkspace = normalizeWorkspaceId(workspaceId);
        if (accountMembershipRepository == null) {
            AccountMembership membership = new AccountMembership();
            membership.setId(IdGenerator.newId());
            membership.setAccountId(account.getId());
            membership.setUserId(user.getId());
            membership.setTenantId(tenantId);
            membership.setWorkspaceId(normalizedWorkspace);
            membership.setStatus("ACTIVE");
            membership.setDefaultMembership(true);
            return membership;
        }

        Optional<AccountMembership> existingMembership = accountMembershipRepository
                .findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                        account.getId(),
                        tenantId,
                        normalizedWorkspace,
                        "ACTIVE");
        if (existingMembership.isPresent()) {
            return existingMembership.get();
        }

        Optional<AccountMembership> legacyDefaultMembership = DEFAULT_WORKSPACE_ID.equals(normalizedWorkspace)
                ? accountMembershipRepository.findByAccountIdAndTenantId(account.getId(), tenantId)
                        .filter(existing -> "ACTIVE".equalsIgnoreCase(existing.getStatus()))
                        .filter(existing -> DEFAULT_WORKSPACE_ID.equals(normalizeWorkspaceId(existing.getWorkspaceId())))
                : Optional.empty();
        if (legacyDefaultMembership.isPresent()) {
            AccountMembership existing = legacyDefaultMembership.get();
            if (!Objects.equals(existing.getWorkspaceId(), normalizedWorkspace)) {
                existing.setWorkspaceId(normalizedWorkspace);
                return accountMembershipRepository.save(existing);
            }
            return existing;
        }

        return Optional.<AccountMembership>empty()
                .orElseGet(() -> {
                    AccountMembership membership = new AccountMembership();
                    membership.setAccountId(account.getId());
                    membership.setUserId(user.getId());
                    membership.setTenantId(tenantId);
                    membership.setWorkspaceId(normalizedWorkspace);
                    membership.setStatus("ACTIVE");
                    membership.setDefaultMembership(DEFAULT_WORKSPACE_ID.equals(normalizedWorkspace));
                    membership.setMetadata(Map.of("legacyUserId", user.getId()));
                    return accountMembershipRepository.save(membership);
                });
    }

    private void ensureRoleBinding(String accountId, String membershipId, String role) {
        if (accountRoleBindingRepository == null) {
            return;
        }
        String roleKey = normalizeRoleKey(role);
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
        binding.setScopeId(null);
        accountRoleBindingRepository.save(binding);
    }

    private void assertInvitationGrantStillAllowed(AuthInvitation invitation, List<String> roles) {
        String inviterUserId = blankToNull(invitation.getInvitedByUserId());
        if (inviterUserId == null) {
            throw new AccessDeniedException("Invitation inviter is not authorized");
        }
        User inviter = userRepository.findByTenantIdAndId(invitation.getTenantId(), inviterUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Invitation inviter is not authorized"));
        MembershipRoles inviterMembership = resolveUserMembershipRoles(
                inviter,
                invitation.getTenantId(),
                invitation.getWorkspaceId(),
                "Invitation inviter membership not found");
        assertUserManagementAllowed(inviterMembership.roles(), "Invitation inviter is not authorized");
        assertGrantAllowed(inviterMembership.roles(), roles, "Invitation roles exceed inviter roles");
    }

    private MembershipRoles resolveUserMembershipRoles(
            User user,
            String tenantId,
            String workspaceId,
            String missingMessage) {
        if (accountRepository == null || accountMembershipRepository == null) {
            String requestedWorkspace = blankToNull(workspaceId);
            if (requestedWorkspace != null && !DEFAULT_WORKSPACE_ID.equals(normalizeWorkspaceId(requestedWorkspace))) {
                throw new AccessDeniedException(missingMessage);
            }
            String fallbackRole = user.getRole() == null || user.getRole().isBlank() ? ROLE_USER : user.getRole();
            return new MembershipRoles(null, null, List.of(normalizeRoleKey(fallbackRole)));
        }

        Account account = accountRepository.findByEmailIgnoreCase(user.getEmail())
                .filter(this::isActiveAccount)
                .orElseThrow(() -> new AccessDeniedException(missingMessage));
        AccountMembership membership = resolveActiveMembershipForContext(
                account.getId(),
                tenantId,
                workspaceId);
        if (!Objects.equals(membership.getUserId(), user.getId())) {
            throw new AccessDeniedException(missingMessage);
        }
        return new MembershipRoles(
                account,
                membership,
                resolveMembershipRoles(account.getId(), membership.getId(), user.getRole()));
    }

    private void assertGrantAllowed(List<String> actorRoles, List<String> requestedRoles, String message) {
        Set<String> assignableRoles = new LinkedHashSet<>();
        List<String> roles = actorRoles == null || actorRoles.isEmpty() ? List.of(ROLE_USER) : actorRoles;
        for (String role : roles) {
            normalizeKnownRoleKey(role)
                    .ifPresent(roleKey -> assignableRoles.addAll(ROLE_GRANT_MATRIX.getOrDefault(roleKey, Set.of())));
        }
        if (!assignableRoles.containsAll(requestedRoles)) {
            throw new AccessDeniedException(message);
        }
    }

    private void assertUserManagementAllowed(List<String> actorRoles, String message) {
        boolean allowed = actorRoles != null && actorRoles.stream()
                .map(this::normalizeKnownRoleKey)
                .flatMap(Optional::stream)
                .anyMatch(USER_MANAGEMENT_ROLES::contains);
        if (!allowed) {
            throw new AccessDeniedException(message);
        }
    }

    private List<String> normalizeRoleKeys(List<String> roles, String defaultRole) {
        if (roles == null || roles.isEmpty()) {
            return List.of(normalizeRoleKey(defaultRole));
        }

        List<String> normalizedRoles = roles.stream()
                .map(this::normalizeRoleKey)
                .distinct()
                .toList();
        return normalizedRoles.isEmpty() ? List.of(normalizeRoleKey(defaultRole)) : normalizedRoles;
    }

    private String normalizeRoleKey(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role key is required");
        }
        String normalized = role.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ALL_ROLE_KEYS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported role key");
        }
        return normalized;
    }

    private Optional<String> normalizeKnownRoleKey(String role) {
        if (role == null || role.isBlank()) {
            return Optional.empty();
        }
        String normalized = role.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return ALL_ROLE_KEYS.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private List<String> resolveMembershipRoles(String accountId, String membershipId, String fallbackRole) {
        if (accountRoleBindingRepository == null) {
            if (fallbackRole != null && !fallbackRole.isBlank()) {
                return List.of(fallbackRole.toUpperCase(Locale.ROOT));
            }
            return List.of(ROLE_USER);
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
        return List.of(ROLE_USER);
    }

    private boolean isActiveAccount(Account account) {
        return account != null && "ACTIVE".equalsIgnoreCase(account.getStatus());
    }

    private AccountMembership resolveActiveMembershipForContext(String accountId, String tenantId, String workspaceId) {
        String requestedWorkspace = blankToNull(workspaceId);
        if (requestedWorkspace != null) {
            return accountMembershipRepository
                    .findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                            accountId,
                            tenantId,
                            normalizeWorkspaceId(requestedWorkspace),
                            "ACTIVE")
                    .orElseThrow(() -> new IllegalArgumentException("Membership not found for target workspace"));
        }

        return accountMembershipRepository
                .findAllByAccountIdAndTenantIdAndStatus(accountId, tenantId, "ACTIVE")
                .stream()
                .filter(AccountMembership::isDefaultMembership)
                .reduce((left, right) -> {
                    throw new IllegalArgumentException("Ambiguous default membership for target tenant");
                })
                .orElseThrow(() -> new IllegalArgumentException("Default membership not found for target tenant"));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String normalizeWorkspaceId(String workspaceId) {
        String normalized = blankToNull(workspaceId);
        return normalized != null ? normalized : DEFAULT_WORKSPACE_ID;
    }

    private Instant resolveInvitationExpiresAt(Instant requestedExpiresAt) {
        Instant now = Instant.now();
        Instant maxExpiresAt = now.plus(MAX_INVITATION_TTL);
        if (requestedExpiresAt == null) {
            return now.plus(DEFAULT_INVITATION_TTL);
        }
        return requestedExpiresAt.isAfter(maxExpiresAt) ? maxExpiresAt : requestedExpiresAt;
    }

    private record MembershipRoles(Account account, AccountMembership membership, List<String> roles) {
    }
}
