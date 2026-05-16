package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.CorePlatformDto;
import com.legent.foundation.service.CorePlatformService;
import com.legent.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/core")
@RequiredArgsConstructor
public class CorePlatformController {

    private final CorePlatformService corePlatformService;

    @PostMapping("/organizations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createOrganization(@Valid @RequestBody CorePlatformDto.OrganizationRequest request) {
        return ApiResponse.ok(corePlatformService.createOrganization(request));
    }

    @GetMapping("/organizations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOrganizations() {
        return ApiResponse.ok(corePlatformService.listOrganizations());
    }

    @PostMapping("/business-units")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createBusinessUnit(@Valid @RequestBody CorePlatformDto.BusinessUnitRequest request) {
        return ApiResponse.ok(corePlatformService.createBusinessUnit(request));
    }

    @GetMapping("/business-units")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listBusinessUnits() {
        return ApiResponse.ok(corePlatformService.listBusinessUnits());
    }

    @GetMapping("/business-units/tree")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listBusinessUnitTree() {
        return ApiResponse.ok(corePlatformService.listBusinessUnitTree());
    }

    @PostMapping("/workspaces")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createWorkspace(@Valid @RequestBody CorePlatformDto.WorkspaceRequest request) {
        return ApiResponse.ok(corePlatformService.createWorkspace(request));
    }

    @GetMapping("/workspaces")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listWorkspaces(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(corePlatformService.listWorkspaces(rolesOf(principal)));
    }

    @PostMapping("/teams")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createTeam(@Valid @RequestBody CorePlatformDto.TeamRequest request) {
        return ApiResponse.ok(corePlatformService.createTeam(request));
    }

    @GetMapping("/teams")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listTeams(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(corePlatformService.listTeams(rolesOf(principal)));
    }

    @PostMapping("/departments")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createDepartment(@Valid @RequestBody CorePlatformDto.DepartmentRequest request) {
        return ApiResponse.ok(corePlatformService.createDepartment(request));
    }

    @GetMapping("/departments")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listDepartments(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(corePlatformService.listDepartments(rolesOf(principal)));
    }

    @PostMapping("/memberships")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createMembership(@Valid @RequestBody CorePlatformDto.MembershipRequest request) {
        return ApiResponse.ok(corePlatformService.createMembership(request));
    }

    @GetMapping("/memberships")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listMemberships(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(corePlatformService.listMemberships(rolesOf(principal)));
    }

    @PostMapping("/roles")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createRoleDefinition(@Valid @RequestBody CorePlatformDto.RoleDefinitionRequest request) {
        return ApiResponse.ok(corePlatformService.createRoleDefinition(request));
    }

    @GetMapping("/roles")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listRoleDefinitions() {
        return ApiResponse.ok(corePlatformService.listRoleDefinitions());
    }

    @PostMapping("/permission-groups")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createPermissionGroup(@Valid @RequestBody CorePlatformDto.PermissionGroupRequest request) {
        return ApiResponse.ok(corePlatformService.createPermissionGroup(request));
    }

    @GetMapping("/permission-groups")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listPermissionGroups() {
        return ApiResponse.ok(corePlatformService.listPermissionGroups());
    }

    @PostMapping("/role-bindings")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createRoleBinding(@Valid @RequestBody CorePlatformDto.RoleBindingRequest request) {
        return ApiResponse.ok(corePlatformService.createRoleBinding(request));
    }

    @GetMapping("/role-bindings")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listRoleBindings() {
        return ApiResponse.ok(corePlatformService.listRoleBindings());
    }

    @PostMapping("/access-grants")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createAccessGrant(@Valid @RequestBody CorePlatformDto.AccessGrantRequest request) {
        return ApiResponse.ok(corePlatformService.createAccessGrant(request));
    }

    @GetMapping("/access-grants")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAccessGrants() {
        return ApiResponse.ok(corePlatformService.listAccessGrants());
    }

    @PostMapping("/access-grants/{id}/decision")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> decideAccessGrant(
            @PathVariable String id,
            @Valid @RequestBody CorePlatformDto.AccessGrantDecisionRequest request) {
        return ApiResponse.ok(corePlatformService.decideAccessGrant(id, request));
    }

    @GetMapping("/access-policy/preview")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<Map<String, Object>> previewAccessPolicy(@RequestParam String principalId) {
        return ApiResponse.ok(corePlatformService.previewAccessPolicy(principalId));
    }

    @PostMapping("/identity-providers")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertIdentityProvider(@Valid @RequestBody CorePlatformDto.IdentityProviderRequest request) {
        return ApiResponse.ok(corePlatformService.upsertIdentityProvider(request));
    }

    @GetMapping("/identity-providers")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listIdentityProviders() {
        return ApiResponse.ok(corePlatformService.listIdentityProviders());
    }

    @PostMapping("/scim-tokens")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createScimToken(@Valid @RequestBody CorePlatformDto.ScimTokenRequest request) {
        return ApiResponse.ok(corePlatformService.createScimToken(request));
    }

    @GetMapping("/scim-tokens")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listScimTokens() {
        return ApiResponse.ok(corePlatformService.listScimTokens());
    }

    @PostMapping("/invitations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createInvitation(@Valid @RequestBody CorePlatformDto.InvitationRequest request) {
        return ApiResponse.ok(corePlatformService.createInvitation(request));
    }

    @PostMapping("/invitations/accept")
    public ApiResponse<Map<String, Object>> acceptInvitation(@Valid @RequestBody CorePlatformDto.InvitationAcceptRequest request) {
        return ApiResponse.ok(corePlatformService.acceptInvitation(request));
    }

    @GetMapping("/invitations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listInvitations() {
        return ApiResponse.ok(corePlatformService.listInvitations());
    }

    @PostMapping("/quotas")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertQuotaPolicy(@Valid @RequestBody CorePlatformDto.QuotaPolicyRequest request) {
        return ApiResponse.ok(corePlatformService.upsertQuotaPolicy(request));
    }

    @GetMapping("/quotas")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listQuotaPolicies() {
        return ApiResponse.ok(corePlatformService.listQuotaPolicies());
    }

    @PostMapping("/usage/increment")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> incrementUsage(@Valid @RequestBody CorePlatformDto.UsageIncrementRequest request) {
        return ApiResponse.ok(corePlatformService.incrementUsage(request));
    }

    @GetMapping("/usage")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listUsage() {
        return ApiResponse.ok(corePlatformService.listUsage());
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createSubscription(@Valid @RequestBody CorePlatformDto.SubscriptionRequest request) {
        return ApiResponse.ok(corePlatformService.createSubscription(request));
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listSubscriptions() {
        return ApiResponse.ok(corePlatformService.listSubscriptions());
    }

    @PostMapping("/environments")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createEnvironment(@Valid @RequestBody CorePlatformDto.EnvironmentRequest request) {
        return ApiResponse.ok(corePlatformService.createEnvironment(request));
    }

    @GetMapping("/environments")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listEnvironments() {
        return ApiResponse.ok(corePlatformService.listEnvironments());
    }

    @PostMapping("/environments/locks")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> lockEnvironment(@Valid @RequestBody CorePlatformDto.EnvironmentLockRequest request) {
        return ApiResponse.ok(corePlatformService.lockEnvironment(request));
    }

    @PostMapping("/promotions")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createPromotion(@Valid @RequestBody CorePlatformDto.PromotionRequest request) {
        return ApiResponse.ok(corePlatformService.createPromotion(request));
    }

    @PostMapping("/promotions/{id}/decision")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> decidePromotion(
            @PathVariable String id,
            @Valid @RequestBody CorePlatformDto.PromotionDecisionRequest request) {
        return ApiResponse.ok(corePlatformService.decidePromotion(id, request));
    }

    @GetMapping("/promotions")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listPromotions() {
        return ApiResponse.ok(corePlatformService.listPromotions());
    }

    @PostMapping("/feature-controls")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertFeatureControl(@Valid @RequestBody CorePlatformDto.FeatureControlRequest request) {
        return ApiResponse.ok(corePlatformService.upsertFeatureControl(request));
    }

    @GetMapping("/feature-controls")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listFeatureControls() {
        return ApiResponse.ok(corePlatformService.listFeatureControls());
    }

    @GetMapping("/audit")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAuditEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(corePlatformService.listAuditEvents(workspaceId, action, limit, rolesOf(principal)));
    }

    private Set<String> rolesOf(UserPrincipal principal) {
        return principal == null || principal.getRoles() == null ? Set.of() : principal.getRoles();
    }
}
