package com.legent.identity.service;

import com.legent.identity.domain.Account;
import com.legent.identity.domain.AccountMembership;
import com.legent.identity.domain.AccountRoleBinding;
import com.legent.identity.domain.AuthInvitation;
import com.legent.identity.domain.User;
import com.legent.identity.dto.AuthBridgeDto;
import com.legent.identity.repository.AccountRoleBindingRepository;
import com.legent.identity.repository.AuthInvitationRepository;
import com.legent.identity.repository.TenantRepository;
import com.legent.identity.repository.UserRepository;
import com.legent.identity.event.IdentityEventPublisher;
import com.legent.identity.repository.AccountMembershipRepository;
import com.legent.identity.repository.AccountRepository;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void createInvitation_whenRequestedRoleExceedsInviter_throwsAndDoesNotSave() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthInvitationRepository invitationRepository = mock(AuthInvitationRepository.class);
        AuthService service = authServiceWithIdentityBridgeRepositories(
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                invitationRepository);
        User inviter = activeUser("user-1", "user@example.com");
        Account inviterAccount = account("requester-account", "user@example.com");
        AccountMembership inviterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);
        AuthBridgeDto.InvitationRequest request = invitationRequest("invitee@example.com", "workspace-owned", List.of("ADMIN"));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(inviter));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(inviterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(inviterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "SECURITY_ADMIN")));

        assertThrows(AccessDeniedException.class,
                () -> service.createInvitation("tenant-1", "user-1", request));

        verify(invitationRepository, never()).save(any(AuthInvitation.class));
    }

    @Test
    void createInvitation_whenRoleAllowed_savesNormalizedRoleKeys() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthInvitationRepository invitationRepository = mock(AuthInvitationRepository.class);
        AuthService service = authServiceWithIdentityBridgeRepositories(
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                invitationRepository);
        User inviter = activeUser("user-1", "user@example.com");
        Account inviterAccount = account("requester-account", "user@example.com");
        AccountMembership inviterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);
        AuthBridgeDto.InvitationRequest request = invitationRequest(
                "Invitee@Example.com",
                "workspace-owned",
                List.of(" viewer ", "ANALYST", "viewer"));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(inviter));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(inviterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(inviterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "ORG_ADMIN")));
        when(invitationRepository.save(any(AuthInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant lowerBound = Instant.now().plus(Duration.ofDays(7)).minusSeconds(1);

        AuthInvitation invitation = service.createInvitation("tenant-1", "user-1", request);

        Instant upperBound = Instant.now().plus(Duration.ofDays(7)).plusSeconds(1);

        ArgumentCaptor<AuthInvitation> invitationCaptor = ArgumentCaptor.forClass(AuthInvitation.class);
        verify(invitationRepository).save(invitationCaptor.capture());
        assertEquals("invitee@example.com", invitationCaptor.getValue().getEmail());
        assertEquals(List.of("VIEWER", "ANALYST"), invitationCaptor.getValue().getRoleKeys());
        assertNotNull(invitationCaptor.getValue().getExpiresAt());
        assertTrue(!invitationCaptor.getValue().getExpiresAt().isBefore(lowerBound));
        assertTrue(!invitationCaptor.getValue().getExpiresAt().isAfter(upperBound));
        assertEquals("user-1", invitation.getInvitedByUserId());
    }

    @Test
    void createInvitation_whenExpiryExceedsMax_clampsToSevenDays() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthInvitationRepository invitationRepository = mock(AuthInvitationRepository.class);
        AuthService service = authServiceWithIdentityBridgeRepositories(
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                invitationRepository);
        User inviter = activeUser("user-1", "user@example.com");
        Account inviterAccount = account("requester-account", "user@example.com");
        AccountMembership inviterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);
        AuthBridgeDto.InvitationRequest request = invitationRequest(
                "invitee@example.com",
                "workspace-owned",
                List.of("VIEWER"));
        request.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(inviter));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(inviterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(inviterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "ORG_ADMIN")));
        when(invitationRepository.save(any(AuthInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant upperBound = Instant.now().plus(Duration.ofDays(7)).plusSeconds(1);

        service.createInvitation("tenant-1", "user-1", request);

        ArgumentCaptor<AuthInvitation> invitationCaptor = ArgumentCaptor.forClass(AuthInvitation.class);
        verify(invitationRepository).save(invitationCaptor.capture());
        assertNotNull(invitationCaptor.getValue().getExpiresAt());
        assertTrue(!invitationCaptor.getValue().getExpiresAt().isAfter(upperBound));
        assertTrue(invitationCaptor.getValue().getExpiresAt().isBefore(request.getExpiresAt()));
    }

    @Test
    void acceptInvitation_bindsRolesToInvitationWorkspaceMembership() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthInvitationRepository invitationRepository = mock(AuthInvitationRepository.class);
        AuthService service = authServiceWithIdentityBridgeRepositories(
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                invitationRepository);

        AuthInvitation invitation = new AuthInvitation();
        invitation.setTenantId("tenant-1");
        invitation.setWorkspaceId("workspace-2");
        invitation.setEmail("invitee@example.com");
        invitation.setToken("invite-token");
        invitation.setRoleKeys(List.of("VIEWER"));
        invitation.setInvitedByUserId("user-1");
        invitation.setStatus("PENDING");
        User inviter = activeUser("user-1", "user@example.com");
        Account inviterAccount = account("requester-account", "user@example.com");
        AccountMembership inviterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-2",
                false);
        Account inviteeAccount = account("invitee-account", "invitee@example.com");
        User inviteeUser = activeUser("invitee-user", "invitee@example.com");
        AccountMembership newWorkspaceMembership = membership(
                "membership-workspace-2",
                "invitee-account",
                "invitee-user",
                "tenant-1",
                "workspace-2",
                false);

        when(invitationRepository.findByToken("invite-token")).thenReturn(Optional.of(invitation));
        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(inviter));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(inviterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-2",
                "ACTIVE"))
                .thenReturn(Optional.of(inviterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "ORG_ADMIN")));
        when(accountRepository.findByEmailIgnoreCase("invitee@example.com")).thenReturn(Optional.of(inviteeAccount));
        when(userRepository.findByTenantIdAndEmailIgnoreCase("tenant-1", "invitee@example.com"))
                .thenReturn(Optional.of(inviteeUser));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "invitee-account",
                "tenant-1",
                "workspace-2",
                "ACTIVE"))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(AccountMembership.class))).thenReturn(newWorkspaceMembership);
        when(roleBindingRepository.findByMembershipId("membership-workspace-2")).thenReturn(List.of());
        when(tokenProvider.generateToken(
                eq("invitee-user"),
                eq("tenant-1"),
                eq("workspace-2"),
                eq(null),
                anyMap()))
                .thenReturn("accepted-token");

        String token = service.acceptInvitation(acceptRequest("invite-token"));

        assertEquals("accepted-token", token);
        verify(membershipRepository, never()).findByAccountIdAndTenantId("invitee-account", "tenant-1");
        ArgumentCaptor<AccountRoleBinding> roleCaptor = ArgumentCaptor.forClass(AccountRoleBinding.class);
        verify(roleBindingRepository).save(roleCaptor.capture());
        assertEquals("membership-workspace-2", roleCaptor.getValue().getMembershipId());
        assertEquals("VIEWER", roleCaptor.getValue().getRoleKey());
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
    void exchangeDelegationToken_whenRequestedRoleExceedsRequester_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthService service = authServiceWithRoleBindings(
                accountRepository,
                membershipRepository,
                roleBindingRepository);
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
        request.setPermissions(List.of("ADMIN"));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(requesterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "SECURITY_ADMIN")));
        when(userRepository.findByTenantIdAndId("tenant-1", "delegated-1")).thenReturn(Optional.of(delegatedUser));
        when(accountRepository.findByEmailIgnoreCase("delegated@example.com")).thenReturn(Optional.of(delegatedAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "delegated-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(delegatedMembership));
        when(roleBindingRepository.findByMembershipId("membership-delegated"))
                .thenReturn(List.of(roleBinding("delegated-account", "membership-delegated", "ADMIN")));

        assertThrows(AccessDeniedException.class,
                () -> service.exchangeDelegationToken("user-1", "tenant-1", request));

        verifyNoInteractions(tokenProvider);
    }

    @Test
    void exchangeDelegationToken_whenRequestedRoleExceedsDelegatedUser_throws() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountMembershipRepository membershipRepository = mock(AccountMembershipRepository.class);
        AccountRoleBindingRepository roleBindingRepository = mock(AccountRoleBindingRepository.class);
        AuthService service = authServiceWithRoleBindings(
                accountRepository,
                membershipRepository,
                roleBindingRepository);
        Account requesterAccount = account("requester-account", "user@example.com");
        AccountMembership requesterMembership = membership(
                "membership-requester",
                "requester-account",
                "user-1",
                "tenant-1",
                "workspace-owned",
                false);
        User delegatedUser = activeUser("delegated-1", "delegated@example.com");
        delegatedUser.setRole("USER");
        Account delegatedAccount = account("delegated-account", "delegated@example.com");
        AccountMembership delegatedMembership = membership(
                "membership-delegated",
                "delegated-account",
                "delegated-1",
                "tenant-1",
                "workspace-owned",
                false);
        AuthBridgeDto.DelegationRequest request = delegationRequest("delegated-1", "workspace-owned");
        request.setPermissions(List.of("VIEWER"));

        when(userRepository.findByTenantIdAndId("tenant-1", "user-1")).thenReturn(Optional.of(activeUser()));
        when(accountRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(requesterAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "requester-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(requesterMembership));
        when(roleBindingRepository.findByMembershipId("membership-requester"))
                .thenReturn(List.of(roleBinding("requester-account", "membership-requester", "ADMIN")));
        when(userRepository.findByTenantIdAndId("tenant-1", "delegated-1")).thenReturn(Optional.of(delegatedUser));
        when(accountRepository.findByEmailIgnoreCase("delegated@example.com")).thenReturn(Optional.of(delegatedAccount));
        when(membershipRepository.findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
                "delegated-account",
                "tenant-1",
                "workspace-owned",
                "ACTIVE"))
                .thenReturn(Optional.of(delegatedMembership));
        when(roleBindingRepository.findByMembershipId("membership-delegated")).thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
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
        request.setPermissions(List.of("VIEWER"));

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

    private AuthService authServiceWithRoleBindings(
            AccountRepository accountRepository,
            AccountMembershipRepository membershipRepository,
            AccountRoleBindingRepository roleBindingRepository) {
        return new AuthService(
                userRepository,
                tenantRepository,
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                null,
                passwordEncoder,
                tokenProvider,
                eventPublisher);
    }

    private AuthService authServiceWithIdentityBridgeRepositories(
            AccountRepository accountRepository,
            AccountMembershipRepository membershipRepository,
            AccountRoleBindingRepository roleBindingRepository,
            AuthInvitationRepository invitationRepository) {
        return new AuthService(
                userRepository,
                tenantRepository,
                accountRepository,
                membershipRepository,
                roleBindingRepository,
                invitationRepository,
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
        account.setStatus("ACTIVE");
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

    private AccountRoleBinding roleBinding(String accountId, String membershipId, String roleKey) {
        AccountRoleBinding binding = new AccountRoleBinding();
        binding.setAccountId(accountId);
        binding.setMembershipId(membershipId);
        binding.setRoleKey(roleKey);
        return binding;
    }

    private AuthBridgeDto.InvitationRequest invitationRequest(String email, String workspaceId, List<String> roleKeys) {
        AuthBridgeDto.InvitationRequest request = new AuthBridgeDto.InvitationRequest();
        request.setEmail(email);
        request.setWorkspaceId(workspaceId);
        request.setRoleKeys(roleKeys);
        return request;
    }

    private AuthBridgeDto.InvitationAcceptRequest acceptRequest(String token) {
        AuthBridgeDto.InvitationAcceptRequest request = new AuthBridgeDto.InvitationAcceptRequest();
        request.setToken(token);
        return request;
    }

    private AuthBridgeDto.DelegationRequest delegationRequest(String delegatedUserId, String workspaceId) {
        AuthBridgeDto.DelegationRequest request = new AuthBridgeDto.DelegationRequest();
        request.setDelegatedUserId(delegatedUserId);
        request.setWorkspaceId(workspaceId);
        return request;
    }
}
