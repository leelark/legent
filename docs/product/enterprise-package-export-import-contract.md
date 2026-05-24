# Enterprise Package Export/Import Contract

Last updated: 2026-05-24.

This is a local Foundation/Admin contract for environment promotion planning. It does not claim Salesforce parity, production readiness, or strict deployment evidence.

## Scope

`foundation-service` exposes:

- `POST /api/v1/global/packages/export`
- `POST /api/v1/global/packages/import/validate`

Both routes require tenant admin permissions through the existing global controller authorization. The service requires current tenant and workspace context and rejects explicit workspace mismatches.

## Supported Manifest Objects

The local manifest version is `legent.enterprise-package.v1`.

Supported object types are:

- `ADMIN_SETTING`
- `FEATURE_CONTROL`
- `GLOBAL_OPERATING_MODEL`
- `DATA_RESIDENCY_POLICY`
- `ENCRYPTION_POLICY`
- `EVIDENCE_PACK`

Denied object families include subscribers, contacts, data-extension rows, campaign recipient snapshots, tracking events, provider responses, raw audit payloads, credentials, private key material, and raw object-store keys.

## Export Behavior

Export reads only Foundation-owned metadata/configuration records in the current tenant and workspace. A source environment is required and must belong to the current workspace.

Export returns a canonical manifest with:

- package key and name
- source tenant, workspace, and environment IDs
- supported object type list
- object payloads with per-object hashes
- dependency edges derived from admin setting dependencies
- required validation gates
- manifest checksum

Encrypted admin settings and sensitive-looking values are blocked before a manifest is returned.

## Import Validation Behavior

Import validation is dry-run only. The endpoint validates:

- target environment ownership
- active target environment lock state
- manifest schema version
- tenant/workspace scope
- manifest checksum
- object type allowlist and denylist
- object hash integrity
- sensitive field/value scan

The response returns `VALIDATED` or `BLOCKED`, a dry-run diff, findings, and a diff summary. Live apply is intentionally unsupported in this slice; requests that set `confirmLiveApply=true` are blocked.

Future live apply work must require explicit confirmation, idempotency, approval/audit evidence, rollback or compensation notes, and object-family transactional safety.
