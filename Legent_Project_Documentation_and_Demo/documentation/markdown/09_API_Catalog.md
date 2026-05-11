# API Catalog

Generated from Spring controller annotations. Request/response body details should be confirmed from DTOs for contract-level API documentation.

| Service | Method | Path | Handler | Source |
| --- | --- | --- | --- | --- |
| audience-service | GET | / | list | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | GET | /{id} | getById | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | POST | / | create | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | DELETE | /{id} | delete | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | POST | /{deId}/records | addRecord | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | GET | /{deId}/records | listRecords | services/audience-service/src/main/java/com/legent/audience/controller/DataExtensionController.java |
| audience-service | POST | / | uploadImport | services/audience-service/src/main/java/com/legent/audience/controller/ImportController.java |
| audience-service | GET | /{id} | getStatus | services/audience-service/src/main/java/com/legent/audience/controller/ImportController.java |
| audience-service | GET | / | listImports | services/audience-service/src/main/java/com/legent/audience/controller/ImportController.java |
| audience-service | POST | /{id}/cancel | cancelImport | services/audience-service/src/main/java/com/legent/audience/controller/ImportController.java |
| audience-service | POST | /mock | startImport | services/audience-service/src/main/java/com/legent/audience/controller/LocalImportController.java |
| audience-service | GET | /{subscriberId} | get | services/audience-service/src/main/java/com/legent/audience/controller/PreferenceController.java |
| audience-service | PUT | /{subscriberId} | update | services/audience-service/src/main/java/com/legent/audience/controller/PreferenceController.java |
| audience-service | POST | /{subscriberId}/pause | pause | services/audience-service/src/main/java/com/legent/audience/controller/PreferenceController.java |
| audience-service | POST | /{subscriberId}/unsubscribe | unsubscribe | services/audience-service/src/main/java/com/legent/audience/controller/PreferenceController.java |
| audience-service | POST | /{subscriberId}/resubscribe | resubscribe | services/audience-service/src/main/java/com/legent/audience/controller/PreferenceController.java |
| audience-service | GET | / | list | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | GET | /{id} | getById | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | POST | / | create | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | PUT | /{id} | update | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | DELETE | /{id} | delete | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | GET | /{id}/evaluate | evaluate | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | POST | /{id}/recompute | recompute | services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java |
| audience-service | POST | /send-eligibility | check | services/audience-service/src/main/java/com/legent/audience/controller/SendEligibilityController.java |
| audience-service | GET | / | list | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | GET | /{id} | getById | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | GET | /key/{subscriberKey} | getByKey | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | POST | / | create | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | PUT | /{id} | update | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | DELETE | /{id} | delete | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | POST | /bulk | bulkUpsert | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | POST | /merge | merge | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | POST | /bulk-actions | bulkActions | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | PUT | /{id}/lifecycle | updateLifecycle | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | PUT | /{id}/score | updateScore | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | GET | /{id}/activity | activity | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | GET | /count | count | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberController.java |
| audience-service | GET | / | list | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | GET | /{id} | getById | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | POST | / | create | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | PUT | /{id} | update | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | DELETE | /{id} | delete | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | POST | /{id}/members | addMembers | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | DELETE | /{id}/members | removeMembers | services/audience-service/src/main/java/com/legent/audience/controller/SubscriberListController.java |
| audience-service | GET | / | list | services/audience-service/src/main/java/com/legent/audience/controller/SuppressionController.java |
| audience-service | POST | / | create | services/audience-service/src/main/java/com/legent/audience/controller/SuppressionController.java |
| audience-service | POST | /bulk | bulkCreate | services/audience-service/src/main/java/com/legent/audience/controller/SuppressionController.java |
| audience-service | GET | /check/{email} | checkCompliance | services/audience-service/src/main/java/com/legent/audience/controller/SuppressionController.java |
| audience-service | DELETE | /{id} | delete | services/audience-service/src/main/java/com/legent/audience/controller/SuppressionController.java |
| automation-service | GET | / | listWorkflows | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | / | createWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{id} | getWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | PUT | /{id} | updateWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/validate | validateWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/definitions | saveWorkflowDefinition | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/publish | publishWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/pause | pauseWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/resume | resumeWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/stop | stopWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/archive | archiveWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/rollback | rollbackWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/clone | cloneWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{id}/versions | listWorkflowVersions | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{id}/versions/{version} | getWorkflowVersion | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/compare | compareVersions | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/trigger | triggerWorkflow | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{id}/runs | listWorkflowRuns | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /runs/{runId} | getRun | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /runs/{runId}/steps | getRunSteps | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /runs/{runId}/trace | getRunTrace | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/simulate | simulate | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/dry-run | dryRun | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{id}/schedules | listSchedules | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | POST | /{id}/schedules | createSchedule | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | PUT | /{id}/schedules/{scheduleId} | updateSchedule | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | DELETE | /{id}/schedules/{scheduleId} | deleteSchedule | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowController.java |
| automation-service | GET | /{workflowId}/latest | getLatestDefinition | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowDefinitionController.java |
| automation-service | POST | / | saveDefinition | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowDefinitionController.java |
| automation-service | GET | /{workflowId}/versions/{version} | getDefinitionVersion | services/automation-service/src/main/java/com/legent/automation/controller/WorkflowDefinitionController.java |
| campaign-service | GET | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER') | list | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | GET | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id} | getById | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER') | create | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | PUT | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id} | update | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | DELETE | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id} | delete | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/clone | cloneCampaign | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/archive | archive | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/restore | restore | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/pause | pause | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/resume | resume | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/cancel | cancel | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | POST | /api/v1/campaignshasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')/{id}/schedule | schedule | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignController.java |
| campaign-service | GET | /campaigns/{id}/experiments | listExperiments | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /campaigns/{id}/experiments | createExperiment | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | PUT | /campaigns/{id}/experiments/{experimentId} | updateExperiment | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | DELETE | /campaigns/{id}/experiments/{experimentId} | deleteExperiment | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /campaigns/{id}/experiments/{experimentId}/promote-winner | promoteWinner | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | GET | /campaigns/{id}/experiments/{experimentId}/metrics | getExperimentMetrics | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | GET | /campaigns/{id}/budget | getBudget | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | PUT | /campaigns/{id}/budget | updateBudget | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | GET | /campaigns/{id}/frequency-policy | getFrequencyPolicy | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | PUT | /campaigns/{id}/frequency-policy | updateFrequencyPolicy | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /campaigns/{id}/send/preflight | preflight | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /campaigns/{id}/send/resend-plans | createResendPlan | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | GET | /send-jobs/{jobId}/dead-letters | listDeadLetters | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /send-jobs/{jobId}/dead-letters/{deadLetterId}/replay | replayDeadLetter | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignEngineController.java |
| campaign-service | POST | /launch-plans/preview | preview | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignLaunchController.java |
| campaign-service | POST | /launch-plans/execute | execute | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignLaunchController.java |
| campaign-service | GET | /{campaignId}/launch-readiness | readiness | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignLaunchController.java |
| campaign-service | POST | /{campaignId}/submit-approval | submitForApproval | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | GET | /{campaignId}/approvals | approvalHistory | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | GET | /approvals/pending | pendingApprovals | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | POST | /approvals/{approvalId}/approve | approve | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | POST | /approvals/{approvalId}/reject | reject | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | POST | /approvals/{approvalId}/cancel | cancel | services/campaign-service/src/main/java/com/legent/campaign/controller/CampaignWorkflowController.java |
| campaign-service | POST | /campaigns/{id}/send | triggerSend | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | GET | /campaigns/{id}/jobs | getJobsForCampaign | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | GET | /send-jobs/{jobId} | getJobStatus | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /campaigns/{id}/send/pause | pause | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /campaigns/{id}/send/resume | resume | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /campaigns/{id}/send/cancel | cancel | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /send-jobs/{jobId}/retry | retry | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /campaigns/{id}/send/resend | resend | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| campaign-service | POST | /campaigns/{id}/trigger-launch | triggerLaunch | services/campaign-service/src/main/java/com/legent/campaign/controller/SendJobController.java |
| content-service | GET | / | listAssets | services/content-service/src/main/java/com/legent/content/controller/AssetController.java |
| content-service | POST | / | uploadAsset | services/content-service/src/main/java/com/legent/content/controller/AssetController.java |
| content-service | POST | /bulk | bulkUpload | services/content-service/src/main/java/com/legent/content/controller/AssetController.java |
| content-service | DELETE | /{id} | deleteAsset | services/content-service/src/main/java/com/legent/content/controller/AssetController.java |
| content-service | POST | / | createBlock | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | GET | /{id} | getBlock | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | PUT | /{id} | updateBlock | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | DELETE | /{id} | deleteBlock | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | GET | / | listBlocks | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | GET | /global | listGlobalBlocks | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | POST | /{id}/versions | createVersion | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | GET | /{id}/versions | listVersions | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | POST | /{id}/versions/{versionNumber}/publish | publishVersion | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | POST | /{id}/rollback/{versionNumber} | rollbackVersion | services/content-service/src/main/java/com/legent/content/controller/ContentBlockController.java |
| content-service | POST | /{templateId}/render | renderTemplate | services/content-service/src/main/java/com/legent/content/controller/ContentController.java |
| content-service | GET | /{templateId}/versions/latest | getLatestVersion | services/content-service/src/main/java/com/legent/content/controller/ContentController.java |
| content-service | POST | / | createEmail | services/content-service/src/main/java/com/legent/content/controller/EmailController.java |
| content-service | GET | /recent | getRecentEmails | services/content-service/src/main/java/com/legent/content/controller/EmailController.java |
| content-service | GET | / | listEmails | services/content-service/src/main/java/com/legent/content/controller/EmailController.java |
| content-service | POST | /content/snippets | createSnippet | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /content/snippets | listSnippets | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | PUT | /content/snippets/{id} | updateSnippet | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | DELETE | /content/snippets/{id} | deleteSnippet | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /personalization-tokens | createToken | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /personalization-tokens | listTokens | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | PUT | /personalization-tokens/{id} | updateToken | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | DELETE | /personalization-tokens/{id} | deleteToken | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /templates/{templateId}/dynamic-content | createDynamicRule | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /templates/{templateId}/dynamic-content | listDynamicRules | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | PUT | /templates/{templateId}/dynamic-content/{id} | updateDynamicRule | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | DELETE | /templates/{templateId}/dynamic-content/{id} | deleteDynamicRule | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /brand-kits | createBrandKit | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /brand-kits | listBrandKits | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | PUT | /brand-kits/{id} | updateBrandKit | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | DELETE | /brand-kits/{id} | deleteBrandKit | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /landing-pages | createLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /landing-pages | listLandingPages | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | PUT | /landing-pages/{id} | updateLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /landing-pages/{id}/publish | publishLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /landing-pages/{id}/archive | archiveLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | DELETE | /landing-pages/{id} | deleteLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /public/landing-pages/{slug} | getPublicLandingPage | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /templates/{templateId}/render | renderTemplate | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /templates/{templateId}/validate | validateTemplate | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | /templates/{templateId}/test-sends | createTestSend | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | GET | /templates/{templateId}/test-sends | listTestSends | services/content-service/src/main/java/com/legent/content/controller/EmailStudioController.java |
| content-service | POST | / | createTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | GET | /{id} | getTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | PUT | /{id} | updateTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | DELETE | /{id} | deleteTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /{id}/clone | cloneTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /{id}/archive | archiveTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /{id}/restore | restoreTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | GET | / | listTemplates | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | GET | /search | searchTemplates | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /import/html | importTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | GET | /{id}/export/html | exportTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /{id}/preview | previewTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /validate | validateTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | /{id}/test-send | testSend | services/content-service/src/main/java/com/legent/content/controller/TemplateController.java |
| content-service | POST | / | createVersion | services/content-service/src/main/java/com/legent/content/controller/TemplateVersionController.java |
| content-service | POST | /{versionNumber}/publish | publishVersion | services/content-service/src/main/java/com/legent/content/controller/TemplateVersionController.java |
| content-service | GET | / | listVersions | services/content-service/src/main/java/com/legent/content/controller/TemplateVersionController.java |
| content-service | GET | /latest | getLatestVersion | services/content-service/src/main/java/com/legent/content/controller/TemplateVersionController.java |
| content-service | GET | /compare | compareVersions | services/content-service/src/main/java/com/legent/content/controller/TemplateVersionController.java |
| content-service | POST | /{templateId}/draft | saveDraft | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /{templateId}/submit-approval | submitForApproval | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | GET | /{templateId}/approvals | approvalHistory | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | GET | /approvals/pending | pendingApprovals | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /approvals/{approvalId}/approve | approveTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /approvals/{approvalId}/reject | rejectTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /approvals/{approvalId}/cancel | cancelApproval | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /{templateId}/publish | publishTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| content-service | POST | /{templateId}/rollback/{versionNumber} | rollbackTemplate | services/content-service/src/main/java/com/legent/content/controller/TemplateWorkflowController.java |
| deliverability-service | GET | /auth/checks | authChecks | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DeliverabilityInsightsController.java |
| deliverability-service | GET | /reputation/telemetry | reputationTelemetry | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DeliverabilityInsightsController.java |
| deliverability-service | GET | /inbox-risk | inboxRisk | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DeliverabilityInsightsController.java |
| deliverability-service | GET | /reports | getReports | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DmarcController.java |
| deliverability-service | POST | /ingest | ingest | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DmarcController.java |
| deliverability-service | GET | / | listDomains | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DomainController.java |
| deliverability-service | POST | / | registerDomain | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DomainController.java |
| deliverability-service | POST | /{domainId}/verify | verifyDomain | services/deliverability-service/src/main/java/com/legent/deliverability/controller/DomainController.java |
| deliverability-service | GET | /{domain} | getScoreByDomain | services/deliverability-service/src/main/java/com/legent/deliverability/controller/ReputationController.java |
| deliverability-service | GET | / | listSuppressions | services/deliverability-service/src/main/java/com/legent/deliverability/controller/SuppressionController.java |
| deliverability-service | GET | /internal | listSuppressionsInternal | services/deliverability-service/src/main/java/com/legent/deliverability/controller/SuppressionController.java |
| deliverability-service | GET | /history | suppressionHistory | services/deliverability-service/src/main/java/com/legent/deliverability/controller/SuppressionController.java |
| delivery-service | GET | /queue/stats | queueStats | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /messages | messages | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | POST | /messages/{messageId}/retry | retryMessage | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | POST | /replay | enqueueReplay | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | POST | /replay/process | processReplay | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /diagnostics/failures | failureDiagnostics | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | POST | /safety/evaluate | evaluateSafety | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /safety/evaluations | safetyEvaluations | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /rate-limits | rateLimits | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /warmup/status | warmupStatus | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | /routing/decision-trace | routingDecisionTrace | services/delivery-service/src/main/java/com/legent/delivery/controller/DeliveryOperationsController.java |
| delivery-service | GET | / | list | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| delivery-service | GET | /health | health | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| delivery-service | POST | /{id}/test | testProvider | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| delivery-service | POST | / | create | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| delivery-service | PUT | /{id} | update | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| delivery-service | DELETE | /{id} | delete | services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java |
| foundation-service | GET | /api/v1/admin/configshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | list | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminConfigController.java |
| foundation-service | POST | /api/v1/admin/configshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | save | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminConfigController.java |
| foundation-service | GET | /api/v1/admin/contact-requestshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | list | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminContactRequestController.java |
| foundation-service | POST | /api/v1/admin/contact-requestshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/{id}/status | updateStatus | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminContactRequestController.java |
| foundation-service | GET | /api/v1/admin/operationshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/dashboard | dashboard | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminOperationsController.java |
| foundation-service | GET | /api/v1/admin/operationshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/access | accessOverview | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminOperationsController.java |
| foundation-service | GET | /api/v1/admin/operationshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/sync-events | syncEvents | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminOperationsController.java |
| foundation-service | GET | /api/v1/admin/public-contenthasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | list | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminPublicContentController.java |
| foundation-service | POST | /api/v1/admin/public-contenthasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | create | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminPublicContentController.java |
| foundation-service | PUT | /api/v1/admin/public-contenthasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/{id} | update | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminPublicContentController.java |
| foundation-service | POST | /api/v1/admin/public-contenthasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/{id}/publish | publish | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminPublicContentController.java |
| foundation-service | GET | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | list | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | POST | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/validate | validate | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | POST | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/apply | apply | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | POST | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/reset | reset | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | GET | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/impact | impact | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | GET | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/history | history | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | POST | /api/v1/admin/settingshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/rollback | rollback | services/foundation-service/src/main/java/com/legent/foundation/controller/AdminSettingsController.java |
| foundation-service | GET | / | listLogs | services/foundation-service/src/main/java/com/legent/foundation/controller/AuditController.java |
| foundation-service | GET | /api/v1/admin/bootstraphasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/status | status | services/foundation-service/src/main/java/com/legent/foundation/controller/BootstrapController.java |
| foundation-service | POST | /api/v1/admin/bootstraphasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')/repair | repair | services/foundation-service/src/main/java/com/legent/foundation/controller/BootstrapController.java |
| foundation-service | GET | /api/v1/admin/brandinghasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | get | services/foundation-service/src/main/java/com/legent/foundation/controller/BrandingController.java |
| foundation-service | POST | /api/v1/admin/brandinghasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | save | services/foundation-service/src/main/java/com/legent/foundation/controller/BrandingController.java |
| foundation-service | GET | /resolve/{key} | resolveConfig | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigController.java |
| foundation-service | GET | / | listConfigs | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigController.java |
| foundation-service | POST | / | createConfig | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigController.java |
| foundation-service | PUT | /{id} | updateConfig | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigController.java |
| foundation-service | DELETE | /{id} | deleteConfig | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigController.java |
| foundation-service | GET | /{configKey}/versions | getConfigVersionHistory | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigVersionController.java |
| foundation-service | GET | /{configKey}/versions/{version} | getConfigVersion | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigVersionController.java |
| foundation-service | GET | /versions | getAllVersionHistory | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigVersionController.java |
| foundation-service | POST | /{configKey}/rollback/{version} | rollbackConfig | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigVersionController.java |
| foundation-service | GET | /{configKey}/compare | compareVersions | services/foundation-service/src/main/java/com/legent/foundation/controller/ConfigVersionController.java |
| foundation-service | POST | /organizations | createOrganization | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /organizations | listOrganizations | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /business-units | createBusinessUnit | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /business-units | listBusinessUnits | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /workspaces | createWorkspace | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /workspaces | listWorkspaces | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /teams | createTeam | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /teams | listTeams | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /departments | createDepartment | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /departments | listDepartments | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /memberships | createMembership | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /memberships | listMemberships | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /roles | createRoleDefinition | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /roles | listRoleDefinitions | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /permission-groups | createPermissionGroup | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /permission-groups | listPermissionGroups | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /access-grants | createAccessGrant | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /access-grants | listAccessGrants | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /invitations | createInvitation | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /invitations/accept | acceptInvitation | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /invitations | listInvitations | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /quotas | upsertQuotaPolicy | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /quotas | listQuotaPolicies | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /usage/increment | incrementUsage | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /usage | listUsage | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /subscriptions | createSubscription | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /subscriptions | listSubscriptions | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /environments | createEnvironment | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /environments | listEnvironments | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /environments/locks | lockEnvironment | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /promotions | createPromotion | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /promotions/{id}/decision | decidePromotion | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /promotions | listPromotions | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | POST | /feature-controls | upsertFeatureControl | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /feature-controls | listFeatureControls | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /audit | listAuditEvents | services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java |
| foundation-service | GET | /evaluate/{key} | evaluate | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | POST | /evaluate/{key}/context | evaluateWithContext | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | GET | / | listFlags | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | GET | /{id} | getFlag | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | POST | / | createFlag | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | PUT | /{id} | updateFlag | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | DELETE | /{id} | deleteFlag | services/foundation-service/src/main/java/com/legent/foundation/controller/FeatureFlagController.java |
| foundation-service | GET | / | health | services/foundation-service/src/main/java/com/legent/foundation/controller/HealthController.java |
| foundation-service | GET | /live | liveness | services/foundation-service/src/main/java/com/legent/foundation/controller/HealthController.java |
| foundation-service | GET | /ready | readiness | services/foundation-service/src/main/java/com/legent/foundation/controller/HealthController.java |
| foundation-service | GET | /content/{page} | contentByPage | services/foundation-service/src/main/java/com/legent/foundation/controller/PublicContentController.java |
| foundation-service | GET | /pricing | pricing | services/foundation-service/src/main/java/com/legent/foundation/controller/PublicContentController.java |
| foundation-service | GET | /blog | blog | services/foundation-service/src/main/java/com/legent/foundation/controller/PublicContentController.java |
| foundation-service | GET | /blog/{slug} | blogBySlug | services/foundation-service/src/main/java/com/legent/foundation/controller/PublicContentController.java |
| foundation-service | POST | /contact | contact | services/foundation-service/src/main/java/com/legent/foundation/controller/PublicContentController.java |
| foundation-service | GET | / | listTenants | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | GET | /{id} | getTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | GET | /slug/{slug} | getTenantBySlug | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | POST | / | createTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | PUT | /{id} | updateTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | DELETE | /{id} | deleteTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | POST | /{id}/suspend | suspendTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | POST | /{id}/restore | restoreTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | POST | /{id}/archive | archiveTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| foundation-service | DELETE | /{id}/hard | hardDeleteTenant | services/foundation-service/src/main/java/com/legent/foundation/controller/TenantController.java |
| identity-service | POST | /login | login | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /signup | signup | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /logout | logout | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /refresh | refresh | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | GET | /session | session | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | GET | /contexts | contexts | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /context/switch | switchContext | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /invitations | createInvitation | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | GET | /invitations | listInvitations | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /invitations/accept | acceptInvitation | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /delegation/exchange | exchangeDelegation | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | GET | /sessions | sessions | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /logout-all | logoutAll | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /forgot-password | forgotPassword | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /reset-password | resetPassword | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /onboarding/start | startOnboarding | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | POST | /onboarding/complete | completeOnboarding | services/identity-service/src/main/java/com/legent/identity/controller/AuthController.java |
| identity-service | GET | / | listUsers | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | GET | /{id} | getUser | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | POST | / | createUser | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | PUT | /{id} | updateUser | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | DELETE | /{id} | deleteUser | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | GET | /preferences | getPreferences | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| identity-service | PUT | /preferences | updatePreferences | services/identity-service/src/main/java/com/legent/identity/controller/UserController.java |
| platform-service | GET | / | getUnreadNotifications | services/platform-service/src/main/java/com/legent/platform/controller/NotificationController.java |
| platform-service | POST | /{id}/read | markAsRead | services/platform-service/src/main/java/com/legent/platform/controller/NotificationController.java |
| platform-service | GET | / | getConfig | services/platform-service/src/main/java/com/legent/platform/controller/PlatformConfigController.java |
| platform-service | POST | / | updateConfig | services/platform-service/src/main/java/com/legent/platform/controller/PlatformConfigController.java |
| platform-service | GET | /api/v1/platform/searchhasAnyRole('ADMIN', 'PLATFORM_ADMIN') | search | services/platform-service/src/main/java/com/legent/platform/controller/SearchController.java |
| platform-service | GET | /api/v1/platform/webhookshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | listWebhooks | services/platform-service/src/main/java/com/legent/platform/controller/WebhookController.java |
| platform-service | POST | /api/v1/platform/webhookshasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN') | createWebhook | services/platform-service/src/main/java/com/legent/platform/controller/WebhookController.java |
| tracking-service | GET | /campaigns | getAllCampaignSummaries | services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java |
| tracking-service | GET | /campaigns/{id} | getCampaignSummary | services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java |
| tracking-service | GET | /events/counts | getEventCounts | services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java |
| tracking-service | GET | /events/timeline | getEventTimeline | services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java |
| tracking-service | GET | /campaigns/{id}/experiments/{experimentId} | getExperimentMetrics | services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java |
| tracking-service | GET | / | getFunnel | services/tracking-service/src/main/java/com/legent/tracking/controller/FunnelController.java |
| tracking-service | GET | /o.gif | trackOpen | services/tracking-service/src/main/java/com/legent/tracking/controller/IngestionController.java |
| tracking-service | GET | /c | trackClick | services/tracking-service/src/main/java/com/legent/tracking/controller/IngestionController.java |
| tracking-service | POST | /events | trackConversion | services/tracking-service/src/main/java/com/legent/tracking/controller/IngestionController.java |
| tracking-service | GET | / | getSegment | services/tracking-service/src/main/java/com/legent/tracking/controller/SegmentController.java |
