package com.legent.identity.service;

import com.legent.identity.domain.Account;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.User;
import com.legent.identity.dto.AuthBridgeDto;
import com.legent.identity.repository.TenantRepository;
import com.legent.identity.repository.UserRepository;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.AccountRepository;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private IdentityEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_whenCredentialsValid_returnsToken() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(tokenProvider.generateToken(eq("user-1"), eq("tenant-1"), anyMap())).thenReturn("jwt-token");

        String token = authService.login(" User@Example.com ", "secret", "tenant-1");

        assertEquals("jwt-token", token);
        verify(userRepository).findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com");
    }

    @Test
    void login_whenUserInactive_throwsBadCredentials() {
        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setActive(false);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> authService.login("user@example.com", "secret", "tenant-1"));

        verifyNoInteractions(passwordEncoder, tokenProvider);
    }

    @Test
    void login_whenPasswordInvalid_throwsBadCredentials() {
        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("encoded");
        user.setActive(true);

        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-secret", "encoded")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login("user@example.com", "bad-secret", "tenant-1"));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void getUserRoles_whenUserExistsAndActive_returnsRole() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("tenant-1");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1"))
                .thenReturn(Optional.of(user));

        var roles = authService.getUserRoles("tenant-1", "user-1");

        assertEquals(1, roles.size());
        assertEquals("ADMIN", roles.get(0));
    }

    @Test
    void getUserRoles_whenUserMissing_returnsEmptyList() {
        when(userRepository.findByTenantIdAndId("tenant-1", "missing"))
                .thenReturn(Optional.empty());

        var roles = authService.getUserRoles("tenant-1", "missing");

        assertTrue(roles.isEmpty());
    }

    @Test
    void switchContext_whenWorkspaceNotOwned_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        User user = activeUser();
        Account account = account();
        AuthBridgeDto.ContextSwitchRequest request = new AuthBridgeDto.ContextSwitchRequest();
        request.setTenantId("tenant-1");
        request.setWorkspaceId("workspace-unowned");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(user));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "account-1",
                "tenant-1",
                "workspace-unowned",
                "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.switchContext("user-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void switchContext_whenWorkspaceOwned_ignoresClientSuppliedEnvironmentAndReturnsToken() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        User user = activeUser();
        Account account = account();
        AccountMembership membership = membership("membership-1", "workspace-owned");
        AuthBridgeDto.ContextSwitchRequest request = new AuthBridgeDto.ContextSwitchRequest();
        request.setTenantId("tenant-1");
        request.setWorkspaceId("workspace-owned");
        request.setEnvironmentId("prod");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(user));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "account-1",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(membership));
        when(tokenProvider.generateToken(
                eq("user-1"),
                eq("tenant-1"),
                eq("workspace-owned"),
                eq(null),
                anyMap()))
                .thenReturn("workspace-token");

        String token = service.switchContext("user-1", request);

        assertEquals("workspace-token", token);
        verify(tokenProvider).generateToken(
                eq("user-1"),
                eq("tenant-1"),
                eq("workspace-owned"),
                eq(null),
                anyMap());
    }

    @Test
    void switchContext_whenWorkspaceBlankAndDefaultMembershipExists_returnsToken() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        User user = activeUser();
        Account account = account();
        AccountMembership defaultMembership = membership("membership-default", "workspace-default");
        defaultMembership.setDefaultMembership(true);
        AuthBridgeDto.ContextSwitchRequest request = new AuthBridgeDto.ContextSwitchRequest();
        request.setTenantId("tenant-1");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(user));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findAllByAccountIdAndTenantIdAndStatus("account-1", "tenant-1", "ACTIVE"))
                .thenReturn(List.of(
                        membership("membership-other", "workspace-other"),
                        defaultMembership));
        when(tokenProvider.generateToken(
                eq("user-1"),
                eq("tenant-1"),
                eq("workspace-default"),
                eq(null),
                anyMap()))
                .thenReturn("default-workspace-token");

        String token = service.switchContext("user-1", request);

        assertEquals("default-workspace-token", token);
    }

    @Test
    void switchContext_whenWorkspaceBlankAndDefaultMembershipAmbiguous_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        User user = activeUser();
        Account account = account();
        AccountMembership defaultOne = membership("membership-default-1", "workspace-default-1");
        AccountMembership defaultTwo = membership("membership-default-2", "workspace-default-2");
        defaultOne.setDefaultMembership(true);
        defaultTwo.setDefaultMembership(true);
        AuthBridgeDto.ContextSwitchRequest request = new AuthBridgeDto.ContextSwitchRequest();
        request.setTenantId("tenant-1");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(user));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(account));
        when(membershipRepository.findAllByAccountIdAndTenantIdAndStatus("account-1", "tenant-1", "ACTIVE"))
                .thenReturn(List.of(defaultOne, defaultTwo));

        assertThrows(IllegalArgumentException.class, () -> service.switchContext("user-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void exchangeDelegationToken_whenDelegatedUserMissing_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        AuthBridgeDto.DelegationRequest request = delegationRequest("delegated-missing", "workspace-owned");
        Account requesterAccount = account("requester-account", "user@example.com");
        AccountMembership requesterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(requesterMembership));
        when(userRepository.findByTenantIdAndId("tenant-1", "delegated-missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.exchangeDelegationToken("user-1", "tenant-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void exchangeDelegationToken_whenRequesterLacksWorkspaceMembership_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        Account requesterAccount = account("requester-account", "user@example.com");
        AuthBridgeDto.DelegationRequest request = delegationRequest("delegated-1", "workspace-requested");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-requested",
                "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.exchangeDelegationToken("user-1", "tenant-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void exchangeDelegationToken_whenDelegatedUserInDifferentWorkspace_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        Account requesterAccount = account("requester-account", "user@example.com");
        AccountMembership requesterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-requested",
                false);
        User delegatedUser = activeUser("delegated-1", "delegated@example.com");
        Account delegatedAccount = account("delegated-account", "delegated@example.com");
        AuthBridgeDto.DelegationRequest request = delegationRequest("delegated-1", "workspace-requested");

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-requested",
                "ACTIVE"))
                .thenReturn(Optional.of(requesterMembership));
        when(userRepository.findByTenantIdAndId("tenant-1", "delegated-1")).thenReturn(Optional.of(delegatedUser));
        when(accountRepository.findByEmailIgnoreCase("delegated@example.com")).thenReturn(Optional.of(delegatedAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "delegated-account",
                "tenant-1",
                "workspace-requested",
                "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.exchangeDelegationToken("user-1", "tenant-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void exchangeDelegationToken_whenDelegatedUserOwnsWorkspace_returnsToken() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AuthService service = authServiceWithAccountRepositories(accountRepository, membershipRepository);
        Account requesterAccount = account("requester-account", "user@example.com");
        AccountMembership requesterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);
        User delegatedUser = activeUser("delegated-1", "delegated@example.com");
        Account delegatedAccount = account("delegated-account", "delegated@example.com");
        AccountMembership delegatedMembership = membership(
                "membership-delegated",
                "delegated-account",
                "delegated-1",
                "tenant-1",
                "workspace-owned",
                false);
        AuthBridgeDto.DelegationRequest request = delegationRequest("delegated-1", "workspace-owned");
        request.setPermissions(List.of("READ_ANALYTICS"));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(requesterMembership));
        when(userRepository.findByTenantIdAndId("tenant-1", "delegated-1")).thenReturn(Optional.of(delegatedUser));
        when(accountRepository.findByEmailIgnoreCase("delegated@example.com")).thenReturn(Optional.of(delegatedAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "delegated-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(delegatedMembership));
        when(tokenProvider.generateToken(
                eq("delegated-1"),
                eq("tenant-1"),
                eq("workspace-owned"),
                eq(null),
                anyMap()))
                .thenReturn("delegated-token");

        String token = service.exchangeDelegationToken("user-1", "tenant-1", request);

        assertEquals("delegated-token", token);
        verify(tokenProvider).generateToken(
                eq("delegated-1"),
                eq("tenant-1"),
                eq("workspace-owned"),
                eq(null),
                anyMap());
    }

    private AuthService authServiceWithAccountRepositories(
            AccountRepository accountRepository,
            AccountMembershipRepository membershipRepository) {
        return new AuthService(
                userRepository,
                tenantRepository,
                accountRepository,
                membershipRepository,
                null,
                null,
                passwordEncoder,
                tokenProvider,
                eventPublisher);
    }

    private User activeUser() {
        return activeUser("user-1", "user@example.com");
    }

    private User activeUser(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setTenantId("tenant-1");
        user.setEmail(email);
        user.setRole("ADMIN");
        user.setActive(true);
        return user;
    }

    private Account account() {
        return account("account-1", "user@example.com");
    }

    private Account account(String id, String email) {
        Account account = new Account();
        account.setId(id);
        account.setEmail(email);
        return account;
    }

    private AccountMembership membership(String id, String workspaceId) {
        return membership(id, "account-1", "user-1", "tenant-1", workspaceId, false);
    }

    private AccountMembership membership(
            String id,
            String accountId,
            String userId,
            String tenantId,
            String workspaceId,
            boolean defaultMembership) {
        AccountMembership membership = new AccountMembership();
        membership.setId(id);
        membership.setAccountId(accountId);
        membership.setUserId(userId);
        membership.setTenantId(tenantId);
        membership.setWorkspaceId(workspaceId);
        membership.setStatus("ACTIVE");
        membership.setDefaultMembership(defaultMembership);
        return membership;
    }

    private AuthBridgeDto.DelegationRequest delegationRequest(String delegatedUserId, String workspaceId) {
        AuthBridgeDto.DelegationRequest request = new AuthBridgeDto.DelegationRequest();
        request.setDelegatedUserId(delegatedUserId);
        request.setWorkspaceId(workspaceId);
        return request;
    }
}
