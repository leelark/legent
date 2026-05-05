package com.legent.foundation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class CorePlatformDto {

    @Getter
    @Setter
    public static class OrganizationRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String slug;
        private String status;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class BusinessUnitRequest {
        @NotBlank
        private String organizationId;
        private String parentId;
        private String code;
        @NotBlank
        private String name;
        private String description;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class WorkspaceRequest {
        @NotBlank
        private String organizationId;
        private String businessUnitId;
        @NotBlank
        private String name;
        @NotBlank
        private String slug;
        private String status;
        private String defaultEnvironment;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class TeamRequest {
        @NotBlank
        private String workspaceId;
        @NotBlank
        private String name;
        private String code;
        private String status;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class DepartmentRequest {
        @NotBlank
        private String workspaceId;
        @NotBlank
        private String name;
        private String code;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class MembershipRequest {
        @NotBlank
        private String userId;
        @NotBlank
        private String organizationId;
        private String businessUnitId;
        private String workspaceId;
        private String teamId;
        private String departmentId;
        private String status;
        private String principalType;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class RoleDefinitionRequest {
        private String tenantId;
        @NotBlank
        private String roleKey;
        @NotBlank
        private String displayName;
        private String description;
        private Boolean system;
        private List<String> permissions;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class PermissionGroupRequest {
        private String tenantId;
        @NotBlank
        private String groupKey;
        @NotBlank
        private String displayName;
        private List<String> permissions;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class AccessGrantRequest {
        @NotBlank
        private String granteeUserId;
        private String workspaceId;
        private List<String> permissions;
        private String reason;
        private Instant expiresAt;
    }

    @Getter
    @Setter
    public static class InvitationRequest {
        @NotBlank
        @Email
        private String email;
        private String organizationId;
        private String workspaceId;
        private List<String> roleKeys;
        private Instant expiresAt;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class InvitationAcceptRequest {
        @NotBlank
        private String token;
    }

    @Getter
    @Setter
    public static class QuotaPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String metricKey;
        private Long softLimit;
        private Long hardLimit;
        private BigDecimal overageRate;
        private Boolean enabled;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class UsageIncrementRequest {
        private String workspaceId;
        @NotBlank
        private String metricKey;
        private Long delta;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }

    @Getter
    @Setter
    public static class SubscriptionRequest {
        @NotBlank
        private String planKey;
        private String billingCycle;
        private Instant startsAt;
        private Instant endsAt;
        private Boolean autoRenew;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class EnvironmentRequest {
        @NotBlank
        private String workspaceId;
        @NotBlank
        private String environmentKey;
        @NotBlank
        private String displayName;
        private String status;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class EnvironmentLockRequest {
        @NotBlank
        private String environmentId;
        @NotBlank
        private String lockType;
        private String reason;
        private Instant expiresAt;
    }

    @Getter
    @Setter
    public static class PromotionRequest {
        @NotBlank
        private String workspaceId;
        @NotBlank
        private String fromEnvironmentId;
        @NotBlank
        private String toEnvironmentId;
        private String summary;
        private Map<String, Object> changeset;
    }

    @Getter
    @Setter
    public static class PromotionDecisionRequest {
        @NotBlank
        private String status;
        private String decisionNote;
    }

    @Getter
    @Setter
    public static class FeatureControlRequest {
        private String workspaceId;
        @NotBlank
        private String featureKey;
        private Boolean enabled;
        private String source;
        private List<String> dependencyKeys;
        private Map<String, Object> metadata;
    }
}
