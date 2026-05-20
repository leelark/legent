# Token-Efficient Memory

Goal: preserve durable intelligence without bloating every future prompt.

Rules:
- `.codex/memory/*.md`: compact current facts, risks, decisions, and next actions only.
- `.codex/backlog/queue.json`: work item source of truth and completed history.
- `.codex/checkpoints/*.json`: session-level progress and recovery.
- `.codex/audit/events/YYYY-MM-DD.jsonl`: detailed 24x7 activity trail.
- `.codex/reports/*.md`: narrative evidence and audits.
- `.codex/dashboards/*.md`: generated summaries.

Do not paste raw command output into memory. Summarize the result and link the command/report/checkpoint.

Memory update budget:
- active files: under 40 lines when possible,
- history files: one concise entry per durable event,
- audit events: one JSONL line per activity milestone,
- reports: concise, dated, indexed.
