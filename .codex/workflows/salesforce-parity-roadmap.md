# Salesforce Parity Roadmap Workflow

Use official current sources for market capability checks when making parity claims. Treat competitor and Salesforce research as evidence collection, not product marketing copy.

## Capability Areas

- Email Studio: template creation, reusable blocks, dynamic content, personalization, testing, approvals, test sends, send classifications, sender/delivery profiles, send logging.
- Contact Builder: subscribers, data extensions, data relationships, imports, preferences, data views, relationship/cardinality governance.
- Journey Builder: event triggers, waits, decisions, goals, exits, re-entry, versioning, simulation, monitoring, high-throughput constraints.
- Automation Studio: imports, SQL/query activities, extracts, file/object storage, schedules, send automation, dependency ordering, run history.
- Deliverability: authentication, warmup, suppressions, feedback loops, DMARC, reputation, safety checks, provider capacity.
- Analytics: campaign, journey, audience, deliverability, provider, engagement, experiment, attribution, and anomaly analytics.
- AI: content assistance, predictive segments, send-time optimization, frequency optimization, channel decisioning, anomaly detection, trust controls.
- Enterprise: governance, approvals, environments, audit, RBAC, SSO/SCIM, evidence-based release, package/export-import.

## Source Schema

Each parity pass must create or update dated notes under `docs/product/competitor-research/` with:

- source ID, URL, access date, source type, vendor, and capability area,
- source facts,
- Legent inference,
- confidence and validation needed,
- gap candidate with backlog status.

Use source IDs like `SRC-SFMC-20260520-001` and cite them from `docs/product/salesforce-parity-matrix.md`.

## Status Rules

- `Covered`: current source code and validation prove the capability.
- `Partial`: current source code proves a subset, but important capability depth is absent or unvalidated.
- `Missing`: no current source evidence proves the capability.
- `Evidence Required`: code exists but external or target-environment proof is still required.
- `Blocked External`: progress depends on external evidence, credentials, provider approval, legal/compliance decision, or target environment access.
- `No Action`: researched capability is intentionally out of scope with rationale.

## Output

Each parity pass records:

- current Legent capability,
- baseline market capability,
- fact IDs,
- inference,
- gap,
- risk,
- required implementation,
- required validation,
- release posture,
- queue item status.

Only mark a gap `READY` when dated source facts, current Legent source audit, narrow scope, owner, acceptance criteria, validation commands, and no blockers are present.
