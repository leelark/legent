# Landing Page Form Policy

Last reviewed: 2026-05-14

## Current policy

Landing page HTML may keep form controls for preview and authoring fidelity, but submitted targets are intentionally inert. The frontend sanitizer (`frontend/src/lib/sanitize-html.ts`) and content-service sanitizer (`EmailContentValidationService.sanitizeLandingPage`) unwrap `<form>` tags and strip `formaction` from submit controls. Inputs, buttons, selects, and textareas may remain as visual controls, but there is no form element left for browser-default submission to the current page URL.

This applies to every target shape until Legent ships a controlled submission endpoint:

- External absolute URLs, such as `https://example.invalid/collect`.
- Relative paths, such as `/__blocked-landing-form-submit__` or `../submit`.
- Protocol-relative URLs, such as `//example.invalid/collect`.
- Encoded vectors, including entity-encoded schemes or slashes.

## Rationale

Landing pages are tenant-owned content, but arbitrary form targets would let authored HTML exfiltrate subscriber data, bypass consent and suppression controls, and create phishing or redirect surfaces. Relative paths are also blocked because Legent does not currently expose a governed landing-form intake endpoint with tenant, workspace, CSRF, consent, rate limit, suppression, audit, and abuse controls.

## Regression coverage

- Frontend: `frontend/tests/e2e/sanitize-html.spec.ts` verifies external, relative, protocol-relative, and encoded `action`/`formaction` vectors are stripped, `<form>` tags are removed, and no default form target is added.
- Backend: `services/content-service/src/test/java/com/legent/content/service/EmailContentValidationServiceTest.java` verifies the same inert-form policy for content-service sanitization.

## Future change gate

Do not allow arbitrary `action` or `formaction` values. If Legent adds landing-form submissions, only allow a controlled Legent endpoint after the endpoint has tenant/workspace binding, origin/CSRF protections, consent and suppression handling, schema validation, rate limiting, audit events, abuse monitoring, and regression tests for rejected external, relative, protocol-relative, and encoded bypass attempts.
