# 0006. Use Flamingock for MongoDB migrations

**Status:** Accepted (2026-11-14)

## Context

ADR 0005 needs a Flyway-style migration tool for MongoDB.

| Option | Verdict |
|---|---|
| Mongock | Maintenance mode, **end of life at the end of 2026** |
| **Flamingock** | Mongock's maintained successor (Apache-2.0), Spring Boot 4 support, same model |
| Liquibase MongoDB | Community extension lags core releases; paid tiers; no Spring Boot starter |
| `IndexOperations.ensureIndex` in a runner | Enough for indexes alone, but no versioning or audit for future data reshapes |

## Decision

Flamingock. Each change is a class (`@Change` with `@Apply` and optional `@Rollback`), ordered by
name prefix. An audit collection records applied changes; a distributed lock stops instances racing.

## Consequences

- Applied changes are immutable: fix forward with a new change.
- A failed change stops the app from starting (manual recovery).
- It tracks its own log, not actual database state; recreating an identical index is a no-op.
- Risk: a young project with a small community. Checking dependency lifecycle (Mongock's EOL)
  was part of the decision.
