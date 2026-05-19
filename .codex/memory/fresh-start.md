# Fresh Start

Created: 2026-05-20.

User requirement:
- Start `.codex/memory` clean.
- Do not retain old memory items.
- Build autonomous operation from the current repository and current task facts.

Fresh-start policy:
- Earlier `.codex/memory` entries are not authoritative.
- Current facts must be rediscovered from source files, commands, tests, and evidence.
- New memory entries must cite date and source.
- Do not store secrets, `.env` values, private keys, tokens, credentials, raw customer data, or generated secret material.

Validation:
- `.codex/utilities/validate-codex-system.ps1` scans memory for known stale-retention markers and validates required `.codex` structure.
