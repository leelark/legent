# Audit Events

Use append-only JSONL for detailed 24x7 activity. This keeps `.codex/memory` compact and fast while preserving the full work trail.

Location:
- `.codex/audit/events/YYYY-MM-DD.jsonl`

Rules:
- One event per line.
- No secrets, `.env` values, private keys, tokens, credentials, customer data, or bulky logs.
- Summaries should be short. Link checkpoints, reports, commands, and files instead of copying long output.
- Memory files should store only durable facts, risks, decisions, fixes, and next actions.
