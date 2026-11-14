# 0005. Manage indexes, validators and seed data as versioned migrations

**Status:** Accepted (2026-11-14)

## Context

Legacy recreates its schema on every boot (`hbm2ddl.auto=create-drop` + `import.sql` seeding
John Smith). That is a demo shortcut; there is no production data to move (in-memory H2).

In MongoDB, collections and fields need no DDL, but **unique indexes, other indexes, `$jsonSchema`
validators and data reshapes** do need managing. Creating indexes at app startup
(`auto-index-creation`) races across instances and is off by default in Spring Data MongoDB.

## Decision

- Indexes and validators ship as **versioned, ordered migrations** (see ADR 0006), run with a
  lock, reviewed in pull requests. `auto-index-creation` stays off.
- Indexes: unique `email`; `name` (sort); unique partial `legacyId`.
- John Smith is **dev / docker-compose seed data only**, never production; tests use their own
  fixtures.

## Consequences

- The unique `email` index is what guarantees uniqueness (legacy's check-then-insert race, B1).
  Without it the race creates real duplicates.
- Least privilege: the app user has `readWrite`; migrations that need `collMod` run with a
  `dbAdmin` user.
- At scale, real data moves by strangling one domain at a time: a Relational Migrator snapshot
  where downtime is acceptable, CDC (Debezium) where it isn't, with reconciliation and a rollback
  trigger. Described, not built.
