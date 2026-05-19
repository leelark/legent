# implement-slice

Purpose: execute one bounded engineering slice.

Steps:

1. Read owning files and tests.
2. Check `git status --short --branch`.
3. Create/update checkpoint.
4. Edit only files in scope.
5. Add focused tests.
6. Run focused validation.
7. Run broader gates if shared/security/release surfaces changed.
8. Update memory.
9. Summarize residual risks.
