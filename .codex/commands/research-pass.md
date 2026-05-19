# research-pass

Purpose: refresh current facts for product parity, security, performance, compliance, dependencies, or market capability work.

Rules:

- Use current official sources for Salesforce, competitor, regulatory, security, platform, provider, and OpenAI/API facts.
- Store only concise dated summaries, links, and implications. Do not copy long copyrighted material.
- Separate facts from inference.
- Convert actionable gaps into backlog candidates through `.codex/commands/refine-backlog.md`.
- Record source freshness in `docs/product/competitor-research/` or an owning report.

Default product research areas:

- Email Studio capability parity.
- Contact Builder and data extension capability parity.
- Journey Builder and Automation Studio parity.
- Deliverability, DNS, DMARC, warmup, feedback loops, suppression, unsubscribe, preference center, and reputation management.
- Analytics, experimentation, attribution, and AI-assisted optimization.
- Enterprise governance, RBAC, SSO/SCIM, approval workflows, audit, environments, and release evidence.

Output:

- current source links and date,
- capability summary,
- Legent current state,
- gap,
- risk,
- recommended implementation slice,
- validation requirement,
- backlog item candidate.
