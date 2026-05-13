# Failed Fixes

Last updated: 2026-05-13.

- No failed fix attempt recorded during orchestration bootstrap.
- 2026-05-13: During Compose health drift fix, two throwaway `docker compose config --quiet` checks with temporary `REDIS_PASSWORD` failed before Compose ran because wrapper quoting was wrong. Cause: shell-wrapper quoting, not Compose config. Avoid repeating by setting/removing `$env:REDIS_PASSWORD` within one PowerShell command and checking `$LASTEXITCODE`.
- 2026-05-13: First Kafka retry/DLQ validation failed in `KafkaConsumerConfigTest.kafkaErrorHandler_usesRecoveringHandlerForDlqPublishing` with `NullPointerException` from `DefaultKafkaConsumerFactory` because the direct unit test called `kafkaListenerContainerFactory()` without setting `@Value` fields. Cause: test setup, not production config. Fixed by populating injected fields with `ReflectionTestUtils` before factory creation.
- 2026-05-13: Initial final Compose health validation failed after stack startup. Signatures: frontend probe attempted `http://127.0.0.1:3000/api/health` while Compose published `3003->3000`, and gateway restarted with Nginx `proxy_http_version directive is duplicate`. Cause: validator used stale/default host port and `/ws/analytics` duplicated a directive already supplied by `proxy_params.conf`. Fixed by resolving frontend port from live Compose metadata and removing the duplicate Nginx directive.

Rule:

- When a fix fails, record command, error signature, attempted change, why it failed, and next safer approach.
