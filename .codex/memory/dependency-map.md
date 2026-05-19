# Dependency Map

Fresh baseline date: 2026-05-20.

Primary dependency surfaces:
- Frontend dependencies are managed under `frontend/package.json` and `frontend/package-lock.json`.
- Backend dependencies are managed by root and module Maven `pom.xml` files.
- Runtime dependencies are declared through Compose, Nginx config, Kubernetes manifests, and ops scripts.
- Shared Java modules are internal dependencies for service modules and must remain stable.

Dependency change rules:
- Change lockfiles only when dependency changes require it.
- Add dependencies only when they reduce real complexity or match established project direction.
- Run focused build/tests and security checks when dependency surfaces change.
