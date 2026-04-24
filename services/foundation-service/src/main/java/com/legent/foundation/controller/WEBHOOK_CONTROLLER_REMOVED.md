# WebhookController Removed

## Reason for Removal (Fix 29)
This controller has been removed to eliminate duplication between foundation-service and platform-service.

## Ownership Clarification
- **Platform Service** now owns: webhook dispatch, notifications, OpenSearch indexing
- **Foundation Service** owns: tenant provisioning, config, feature flags, audit

## Migration Path
Use `/api/v1/platform/webhooks` endpoints in platform-service instead.
The platform-service provides a more advanced WebhookDispatcherService with:
- HMAC signature generation
- Event filtering by subscription
- Retry with exponential backoff
- Webhook delivery logging
- WebClient (non-blocking) support
