# Database And Migrations

## Migration Inventory

| Service | Count | Migrations |
| --- | --- | --- |
| audience-service | 26 | V10__add_created_at_to_list_memberships.sql<br>V11__add_created_at_to_segment_memberships.sql<br>V12__add_updated_at_to_suppressions.sql<br>V13__workspace_scope_and_idempotency.sql<br>V1__audience_schema.sql<br>V2__consent_and_double_optin.sql<br>V3__add_deleted_at_to_consent_records.sql<br>V4__add_version_to_consent_records.sql<br>V5__add_created_by_to_data_extension_fields.sql<br>V6__add_base_entity_columns_to_all_tables.sql<br>V7__add_updated_at_to_all_tables.sql<br>V8__add_created_by_to_all_tables.sql<br>V9__add_all_base_entity_to_data_extension_records.sql<br>V10__add_created_at_to_list_memberships.sql<br>V11__add_created_at_to_segment_memberships.sql<br>V12__add_updated_at_to_suppressions.sql<br>V13__workspace_scope_and_idempotency.sql<br>V1__audience_schema.sql<br>V2__consent_and_double_optin.sql<br>V3__add_deleted_at_to_consent_records.sql<br>V4__add_version_to_consent_records.sql<br>V5__add_created_by_to_data_extension_fields.sql<br>V6__add_base_entity_columns_to_all_tables.sql<br>V7__add_updated_at_to_all_tables.sql<br>V8__add_created_by_to_all_tables.sql<br>V9__add_all_base_entity_to_data_extension_records.sql |
| automation-service | 6 | V1__automation_schema.sql<br>V3__fix_workflow_version_datatype.sql<br>V4__automation_workspace_scope_and_idempotency.sql<br>V1__automation_schema.sql<br>V3__fix_workflow_version_datatype.sql<br>V4__automation_workspace_scope_and_idempotency.sql |
| campaign-service | 26 | V10__add_all_base_entity_columns_to_send_job_checkpoints.sql<br>V11__campaign_workspace_lifecycle_and_idempotency.sql<br>V12__campaign_engine_enterprise.sql<br>V13__campaign_launch_command_center.sql<br>V1__campaign_schema.sql<br>V2__campaign_approval_and_checkpoint.sql<br>V3__add_content_id_to_campaigns.sql<br>V4__add_missing_columns.sql<br>V5__add_scheduled_at_to_campaigns.sql<br>V6__add_missing_send_batches_columns.sql<br>V7__add_created_by_to_send_job_checkpoints.sql<br>V8__add_deleted_at_to_send_job_checkpoints.sql<br>V9__add_updated_at_to_send_job_checkpoints.sql<br>V10__add_all_base_entity_columns_to_send_job_checkpoints.sql<br>V11__campaign_workspace_lifecycle_and_idempotency.sql<br>V12__campaign_engine_enterprise.sql<br>V13__campaign_launch_command_center.sql<br>V1__campaign_schema.sql<br>V2__campaign_approval_and_checkpoint.sql<br>V3__add_content_id_to_campaigns.sql<br>V4__add_missing_columns.sql<br>V5__add_scheduled_at_to_campaigns.sql<br>V6__add_missing_send_batches_columns.sql<br>V7__add_created_by_to_send_job_checkpoints.sql<br>V8__add_deleted_at_to_send_job_checkpoints.sql<br>V9__add_updated_at_to_send_job_checkpoints.sql |
| content-service | 14 | V1__content_schema.sql<br>V2__template_workflow.sql<br>V3__add_created_by_to_template_approvals.sql<br>V4__add_deleted_at_and_version_to_template_approvals.sql<br>V5__add_base_entity_columns_to_template_versions.sql<br>V6__add_updated_at_to_template_versions.sql<br>V7__email_studio_enterprise.sql<br>V1__content_schema.sql<br>V2__template_workflow.sql<br>V3__add_created_by_to_template_approvals.sql<br>V4__add_deleted_at_and_version_to_template_approvals.sql<br>V5__add_base_entity_columns_to_template_versions.sql<br>V6__add_updated_at_to_template_versions.sql<br>V7__email_studio_enterprise.sql |
| deliverability-service | 14 | V1__deliverability_schema.sql<br>V2__dmarc_reporting.sql<br>V3__add_domain_status.sql<br>V4__add_created_by_to_sender_domains.sql<br>V5__add_deleted_at_and_version_to_sender_domains.sql<br>V6__add_missing_sender_domain_columns.sql<br>V7__workspace_scope_and_event_idempotency.sql<br>V1__deliverability_schema.sql<br>V2__dmarc_reporting.sql<br>V3__add_domain_status.sql<br>V4__add_created_by_to_sender_domains.sql<br>V5__add_deleted_at_and_version_to_sender_domains.sql<br>V6__add_missing_sender_domain_columns.sql<br>V7__workspace_scope_and_event_idempotency.sql |
| delivery-service | 22 | V10__inbox_first_delivery_intelligence.sql<br>V11__campaign_experiment_lineage.sql<br>V1__delivery_schema.sql<br>V2__encrypt_provider_passwords.sql<br>V3__provider_health_and_replay.sql<br>V4__fix_message_logs_audit_fields.sql<br>V5__add_content_reference_to_message_logs.sql<br>V6__fix_provider_health_audit_fields.sql<br>V7__fix_suppression_signals_audit_fields.sql<br>V8__message_logs_campaign_reconciliation.sql<br>V9__workspace_strict_idempotency_and_lineage.sql<br>V10__inbox_first_delivery_intelligence.sql<br>V11__campaign_experiment_lineage.sql<br>V1__delivery_schema.sql<br>V2__encrypt_provider_passwords.sql<br>V3__provider_health_and_replay.sql<br>V4__fix_message_logs_audit_fields.sql<br>V5__add_content_reference_to_message_logs.sql<br>V6__fix_provider_health_audit_fields.sql<br>V7__fix_suppression_signals_audit_fields.sql<br>V8__message_logs_campaign_reconciliation.sql<br>V9__workspace_strict_idempotency_and_lineage.sql |
| foundation-service | 22 | V10__admin_operations_sync_events.sql<br>V11__widen_platform_core_tenant_ids.sql<br>V1__foundation_schema.sql<br>V2__admin_schema.sql<br>V3__initial_data.sql<br>V4__tenant_audit_and_config_versioning.sql<br>V5__add_config_version_to_system_configs.sql<br>V6__platform_core_foundation.sql<br>V7__admin_settings_engine_and_bootstrap.sql<br>V8__public_content_cms.sql<br>V9__public_contact_requests.sql<br>V10__admin_operations_sync_events.sql<br>V11__widen_platform_core_tenant_ids.sql<br>V1__foundation_schema.sql<br>V2__admin_schema.sql<br>V3__initial_data.sql<br>V4__tenant_audit_and_config_versioning.sql<br>V5__add_config_version_to_system_configs.sql<br>V6__platform_core_foundation.sql<br>V7__admin_settings_engine_and_bootstrap.sql<br>V8__public_content_cms.sql<br>V9__public_contact_requests.sql |
| identity-service | 16 | V1__identity_schema.sql<br>V3__identity_constraints.sql<br>V4__add_base_entity_columns_to_users.sql<br>V5__initial_data.sql<br>V6__add_refresh_tokens.sql<br>V7__platform_core_identity_bridge.sql<br>V8__widen_identity_bridge_ids.sql<br>V9__auth_recovery_onboarding_and_preferences.sql<br>V1__identity_schema.sql<br>V3__identity_constraints.sql<br>V4__add_base_entity_columns_to_users.sql<br>V5__initial_data.sql<br>V6__add_refresh_tokens.sql<br>V7__platform_core_identity_bridge.sql<br>V8__widen_identity_bridge_ids.sql<br>V9__auth_recovery_onboarding_and_preferences.sql |
| platform-service | 8 | V1__platform_schema.sql<br>V2__platform_webhooks_soft_delete.sql<br>V3__add_webhook_retries_table.sql<br>V4__add_webhook_retries_fk.sql<br>V1__platform_schema.sql<br>V2__platform_webhooks_soft_delete.sql<br>V3__add_webhook_retries_table.sql<br>V4__add_webhook_retries_fk.sql |
| tracking-service | 14 | V1__tracking_schema.sql<br>V3__tracking_hardening.sql<br>V4__tracking_hourly_agg.sql<br>V5__enhanced_tracking.sql<br>V6__add_created_at_to_subscriber_summaries.sql<br>V7__tracking_workspace_and_idempotency.sql<br>V8__campaign_experiment_lineage.sql<br>V1__tracking_schema.sql<br>V3__tracking_hardening.sql<br>V4__tracking_hourly_agg.sql<br>V5__enhanced_tracking.sql<br>V6__add_created_at_to_subscriber_summaries.sql<br>V7__tracking_workspace_and_idempotency.sql<br>V8__campaign_experiment_lineage.sql |

## Entity Inventory

| Service | Entity | Table | Source |
| --- | --- | --- | --- |
| audience-service | ConsentRecord | consent_records | services/audience-service/src/main/java/com/legent/audience/domain/ConsentRecord.java |
| audience-service | DataExtension | data_extensions | services/audience-service/src/main/java/com/legent/audience/domain/DataExtension.java |
| audience-service | DataExtensionField | data_extension_fields | services/audience-service/src/main/java/com/legent/audience/domain/DataExtensionField.java |
| audience-service | DataExtensionRecord | data_extension_records | services/audience-service/src/main/java/com/legent/audience/domain/DataExtensionRecord.java |
| audience-service | DoubleOptInToken | double_optin_tokens | services/audience-service/src/main/java/com/legent/audience/domain/DoubleOptInToken.java |
| audience-service | ImportJob | import_jobs | services/audience-service/src/main/java/com/legent/audience/domain/ImportJob.java |
| audience-service | ListMembership | list_memberships | services/audience-service/src/main/java/com/legent/audience/domain/ListMembership.java |
| audience-service | Segment | segments | services/audience-service/src/main/java/com/legent/audience/domain/Segment.java |
| audience-service | SegmentMembership | segment_memberships | services/audience-service/src/main/java/com/legent/audience/domain/SegmentMembership.java |
| audience-service | Subscriber | subscribers | services/audience-service/src/main/java/com/legent/audience/domain/Subscriber.java |
| audience-service | SubscriberList | subscriber_lists | services/audience-service/src/main/java/com/legent/audience/domain/SubscriberList.java |
| audience-service | Suppression | suppressions | services/audience-service/src/main/java/com/legent/audience/domain/Suppression.java |
| automation-service | InstanceHistory | instance_history | services/automation-service/src/main/java/com/legent/automation/domain/InstanceHistory.java |
| automation-service | Workflow | workflows | services/automation-service/src/main/java/com/legent/automation/domain/Workflow.java |
| automation-service | WorkflowDefinition | workflow_definitions | services/automation-service/src/main/java/com/legent/automation/domain/WorkflowDefinition.java |
| automation-service | WorkflowInstance | workflow_instances | services/automation-service/src/main/java/com/legent/automation/domain/WorkflowInstance.java |
| automation-service | WorkflowSchedule | workflow_schedules | services/automation-service/src/main/java/com/legent/automation/domain/WorkflowSchedule.java |
| campaign-service | Campaign | campaigns | services/campaign-service/src/main/java/com/legent/campaign/domain/Campaign.java |
| campaign-service | CampaignApproval | campaign_approvals | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignApproval.java |
| campaign-service | CampaignAudience | campaign_audiences | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignAudience.java |
| campaign-service | CampaignBudget | campaign_budgets | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignBudget.java |
| campaign-service | CampaignDeadLetter | campaign_dead_letters | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignDeadLetter.java |
| campaign-service | CampaignExperiment | campaign_experiments | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignExperiment.java |
| campaign-service | CampaignFrequencyPolicy | campaign_frequency_policies | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignFrequencyPolicy.java |
| campaign-service | CampaignLaunchPlan | campaign_launch_plans | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignLaunchPlan.java |
| campaign-service | CampaignLaunchStep | campaign_launch_steps | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignLaunchStep.java |
| campaign-service | CampaignLock | campaign_locks | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignLock.java |
| campaign-service | CampaignSendLedger | campaign_send_ledger | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignSendLedger.java |
| campaign-service | CampaignVariant | campaign_variants | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignVariant.java |
| campaign-service | CampaignVariantMetric | campaign_variant_metrics | services/campaign-service/src/main/java/com/legent/campaign/domain/CampaignVariantMetric.java |
| campaign-service | SendBatch | send_batches | services/campaign-service/src/main/java/com/legent/campaign/domain/SendBatch.java |
| campaign-service | SendJob | send_jobs | services/campaign-service/src/main/java/com/legent/campaign/domain/SendJob.java |
| campaign-service | SendJobCheckpoint | send_job_checkpoints | services/campaign-service/src/main/java/com/legent/campaign/domain/SendJobCheckpoint.java |
| campaign-service | ThrottlingRule | throttling_rules | services/campaign-service/src/main/java/com/legent/campaign/domain/ThrottlingRule.java |
| content-service | Asset | assets | services/content-service/src/main/java/com/legent/content/domain/Asset.java |
| content-service | BrandKit | brand_kits | services/content-service/src/main/java/com/legent/content/domain/BrandKit.java |
| content-service | ContentBlock | content_blocks | services/content-service/src/main/java/com/legent/content/domain/ContentBlock.java |
| content-service | ContentBlockVersion | content_block_versions | services/content-service/src/main/java/com/legent/content/domain/ContentBlockVersion.java |
| content-service | ContentSnippet | content_snippets | services/content-service/src/main/java/com/legent/content/domain/ContentSnippet.java |
| content-service | DynamicContentRule | dynamic_content_rules | services/content-service/src/main/java/com/legent/content/domain/DynamicContentRule.java |
| content-service | Email | emails | services/content-service/src/main/java/com/legent/content/domain/Email.java |
| content-service | EmailTemplate | email_templates | services/content-service/src/main/java/com/legent/content/domain/EmailTemplate.java |
| content-service | LandingPage | landing_pages | services/content-service/src/main/java/com/legent/content/domain/LandingPage.java |
| content-service | PersonalizationToken | personalization_tokens | services/content-service/src/main/java/com/legent/content/domain/PersonalizationToken.java |
| content-service | RenderValidationReport | render_validation_reports | services/content-service/src/main/java/com/legent/content/domain/RenderValidationReport.java |
| content-service | TemplateApproval | template_approvals | services/content-service/src/main/java/com/legent/content/domain/TemplateApproval.java |
| content-service | TemplateTestSendRecord | template_test_send_records | services/content-service/src/main/java/com/legent/content/domain/TemplateTestSendRecord.java |
| content-service | TemplateVersion | template_versions | services/content-service/src/main/java/com/legent/content/domain/TemplateVersion.java |
| deliverability-service | DmarcReport | dmarc_reports | services/deliverability-service/src/main/java/com/legent/deliverability/domain/DmarcReport.java |
| deliverability-service | DomainConfig | domains | services/deliverability-service/src/main/java/com/legent/deliverability/domain/DomainConfig.java |
| deliverability-service | DomainReputation | domain_reputations | services/deliverability-service/src/main/java/com/legent/deliverability/domain/DomainReputation.java |
| deliverability-service | ReputationScore | reputation_scores | services/deliverability-service/src/main/java/com/legent/deliverability/domain/ReputationScore.java |
| deliverability-service | SenderDomain | sender_domains | services/deliverability-service/src/main/java/com/legent/deliverability/domain/SenderDomain.java |
| deliverability-service | SuppressionList | suppression_list | services/deliverability-service/src/main/java/com/legent/deliverability/domain/SuppressionList.java |
| delivery-service | DeliveryReplayQueue | delivery_replay_queue | services/delivery-service/src/main/java/com/legent/delivery/domain/DeliveryReplayQueue.java |
| delivery-service | InboxSafetyEvaluation | delivery_safety_evaluations | services/delivery-service/src/main/java/com/legent/delivery/domain/InboxSafetyEvaluation.java |
| delivery-service | IpPool | ip_pools | services/delivery-service/src/main/java/com/legent/delivery/domain/IpPool.java |
| delivery-service | MessageLog | message_logs | services/delivery-service/src/main/java/com/legent/delivery/domain/MessageLog.java |
| delivery-service | ProviderDecisionTrace | provider_decision_traces | services/delivery-service/src/main/java/com/legent/delivery/domain/ProviderDecisionTrace.java |
| delivery-service | ProviderHealthCheck | provider_health_checks | services/delivery-service/src/main/java/com/legent/delivery/domain/ProviderHealthCheck.java |
| delivery-service | ProviderHealthStatus | provider_health_status | services/delivery-service/src/main/java/com/legent/delivery/domain/ProviderHealthStatus.java |
| delivery-service | RoutingRule | routing_rules | services/delivery-service/src/main/java/com/legent/delivery/domain/RoutingRule.java |
| delivery-service | SendRateState | delivery_send_rate_state | services/delivery-service/src/main/java/com/legent/delivery/domain/SendRateState.java |
| delivery-service | SmtpProvider | smtp_providers | services/delivery-service/src/main/java/com/legent/delivery/domain/SmtpProvider.java |
| delivery-service | SuppressionSignal | suppression_signals | services/delivery-service/src/main/java/com/legent/delivery/domain/SuppressionSignal.java |
| delivery-service | WarmupState | delivery_warmup_state | services/delivery-service/src/main/java/com/legent/delivery/domain/WarmupState.java |
| foundation-service | AdminConfig | admin_configs | services/foundation-service/src/main/java/com/legent/foundation/domain/AdminConfig.java |
| foundation-service | AuditLog | audit_logs | services/foundation-service/src/main/java/com/legent/foundation/domain/AuditLog.java |
| foundation-service | Branding | branding | services/foundation-service/src/main/java/com/legent/foundation/domain/Branding.java |
| foundation-service | ConfigVersionHistory | config_version_history | services/foundation-service/src/main/java/com/legent/foundation/domain/ConfigVersionHistory.java |
| foundation-service | FeatureFlag | feature_flags | services/foundation-service/src/main/java/com/legent/foundation/domain/FeatureFlag.java |
| foundation-service | PublicContactRequest | public_contact_requests | services/foundation-service/src/main/java/com/legent/foundation/domain/PublicContactRequest.java |
| foundation-service | PublicContent | public_contents | services/foundation-service/src/main/java/com/legent/foundation/domain/PublicContent.java |
| foundation-service | SystemConfig | system_configs | services/foundation-service/src/main/java/com/legent/foundation/domain/SystemConfig.java |
| foundation-service | Tenant | tenants | services/foundation-service/src/main/java/com/legent/foundation/domain/Tenant.java |
| foundation-service | TenantAuditLog | tenant_audit_log | services/foundation-service/src/main/java/com/legent/foundation/domain/TenantAuditLog.java |
| foundation-service | TenantBootstrapStatus | tenant_bootstrap_status | services/foundation-service/src/main/java/com/legent/foundation/domain/TenantBootstrapStatus.java |
| identity-service | Account | accounts | services/identity-service/src/main/java/com/legent/identity/domain/Account.java |
| identity-service | AccountMembership | account_memberships | services/identity-service/src/main/java/com/legent/identity/domain/AccountMembership.java |
| identity-service | AccountRoleBinding | account_role_bindings | services/identity-service/src/main/java/com/legent/identity/domain/AccountRoleBinding.java |
| identity-service | AccountSession | account_sessions | services/identity-service/src/main/java/com/legent/identity/domain/AccountSession.java |
| identity-service | AuthInvitation | auth_invitations | services/identity-service/src/main/java/com/legent/identity/domain/AuthInvitation.java |
| identity-service | OnboardingState | onboarding_states | services/identity-service/src/main/java/com/legent/identity/domain/OnboardingState.java |
| identity-service | PasswordResetToken | password_reset_tokens | services/identity-service/src/main/java/com/legent/identity/domain/PasswordResetToken.java |
| identity-service | RefreshToken | refresh_tokens | services/identity-service/src/main/java/com/legent/identity/domain/RefreshToken.java |
| identity-service | Tenant | tenants | services/identity-service/src/main/java/com/legent/identity/domain/Tenant.java |
| identity-service | User | users | services/identity-service/src/main/java/com/legent/identity/domain/User.java |
| identity-service | UserPreference | user_preferences | services/identity-service/src/main/java/com/legent/identity/domain/UserPreference.java |
| platform-service | Notification | notifications | services/platform-service/src/main/java/com/legent/platform/domain/Notification.java |
| platform-service | SearchIndexDoc | search_index_docs | services/platform-service/src/main/java/com/legent/platform/domain/SearchIndexDoc.java |
| platform-service | TenantConfig | tenant_configs | services/platform-service/src/main/java/com/legent/platform/domain/TenantConfig.java |
| platform-service | WebhookConfig | webhooks | services/platform-service/src/main/java/com/legent/platform/domain/WebhookConfig.java |
| platform-service | WebhookLog | webhook_logs | services/platform-service/src/main/java/com/legent/platform/domain/WebhookLog.java |
| platform-service | WebhookRetry | webhook_retries | services/platform-service/src/main/java/com/legent/platform/domain/WebhookRetry.java |
| tracking-service | CampaignSummary | campaign_summaries | services/tracking-service/src/main/java/com/legent/tracking/domain/CampaignSummary.java |
| tracking-service | RawEvent | raw_events | services/tracking-service/src/main/java/com/legent/tracking/domain/RawEvent.java |
| tracking-service | SubscriberSummary | subscriber_summaries | services/tracking-service/src/main/java/com/legent/tracking/domain/SubscriberSummary.java |

## Ownership Rules

- Each service owns its database schema and Flyway migration timeline.
- Cross-service state changes should flow through APIs or Kafka events, not direct database writes.
- Local Compose creates databases through `postgres-init`.
- Backup and restore helpers live under `scripts/ops/backup-restore.ps1`.
