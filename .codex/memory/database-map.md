# Database Map

Fresh baseline date: 2026-05-20.

Source: repository service layout and architecture docs.

Current database posture:
- PostgreSQL is the primary transactional database for service-owned schemas.
- ClickHouse is used for analytics and high-volume event analysis.
- Redis is used for cache/rate/coordination concerns where explicitly scoped.
- Flyway owns schema migrations. Do not edit historical migrations that may already be applied.
- Production behavior must not use Hibernate `ddl-auto=update`.

Open follow-up:
- Run a schema ownership audit before broad data model changes.
