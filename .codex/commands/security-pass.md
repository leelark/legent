# security-pass

Purpose: remove security and compliance blockers.

Discovery:

```powershell
rg -n "permitAll|csrf|SameSite|HttpOnly|TenantContext|X-Tenant-Id|trusted\.packages|ddl-auto" services shared frontend config infrastructure -g "!**/target/**" -g "!**/node_modules/**" -g "!**/.next/**"
rg -n "dangerouslySetInnerHTML|postPublic|withCredentials|localStorage|sessionStorage" frontend/src frontend/tests -g "*.ts" -g "*.tsx"
```

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
cd frontend
npm audit --omit=dev --audit-level=high
```

Security invariants:

- No secrets or `.env` values are printed or committed.
- Cookie auth, SameSite, secure flags, refresh path scoping, and unsafe-method origin/referer guard remain intact.
- Protected endpoints fail closed without tenant/workspace context.
- `permitAll` is only for intentional public endpoints with tests.
- Kafka trusted packages stay narrow.
- Production uses Flyway and `ddl-auto=validate`.
- HTML sanitization, outbound URL guard, signed tracking, suppressions, unsubscribe, warmup, and rate controls are preserved.

Record findings in `security-findings.md`.
