# Data migration

How member data would move from the legacy relational database to MongoDB, and how the same
approach scales to a larger system. Phase 6 of the migration plan; issue #23.

## What actually migrates here

Legacy kitchensink keeps its data in **in-memory H2**, recreated on every start
(`hbm2ddl.auto=create-drop` plus `import.sql` for John Smith). There is no production data to move.
What migrated is the **database setup**, now versioned Flamingock changes that run before the app
accepts traffic (ADRs 0005, 0006, 0011):

| Legacy, at every start | New app |
|---|---|
| Hibernate creates the `Member` table | `_0001` creates `members` with a `$jsonSchema` validator |
| Unique constraint on `email` | `_0002` unique `email` index; `_0006` lower-cases existing emails (gap B2) |
| (sort done by the query) | `_0003`, then `_0007`: `{name, _id}` index for the list and paging |
| `Member_SEQ` sequence for ids | ObjectId `_id`; `_0004` unique partial index on `legacyId` (ADR 0002) |
| `import.sql` inserts John Smith | `_0005`, `dev` profile only |

The rest of this document is the plan for a deployment that did have data, written as if legacy
ran on a real database (PostgreSQL, say). 

## Transform spec: `Member` table → `members` collection

| Legacy column | Type | New field | Rule |
|---|---|---|---|
| — | — | `_id` | New ObjectId, generated on insert |
| `id` | `BIGINT` (from `Member_SEQ`) | `legacyId` | Copied as a 64-bit integer (`long`, which the validator requires). Unique; only migrated documents have it |
| `name` | `VARCHAR(25)`, not null | `name` | As is |
| `email` | `VARCHAR(255)`, not null, unique | `email` | **Lower-cased** with Java's `toLowerCase(Locale.ROOT)`, as the app stores new emails (gap B2) |
| `phone_number` | `VARCHAR(12)`, not null | `phoneNumber` | As is |

- **No nulls, no missing fields:** legacy's columns are `NOT NULL` (Hibernate derives that from
  `@NotNull`), and the validator requires all three fields.
- **No collation:** the list keeps legacy's binary, case-sensitive order (ADR 0009).
- **Ids in the API:** responses and paths use the ObjectId (ADR 0002). Kitchensink has no external
  consumers storing member ids; a system that has them would also accept `legacyId` on the API
  for a deprecation period.

### Data legacy accepted that the new rules don't

Legacy stored values the new app now rejects: names of only spaces (B3), names with symbols, emoji
or non-ASCII digits (B11, B19), phone numbers like `+123456789` or `1.23456789E9` (B18). They are
**migrated as they are**:

- the database validator still follows legacy's rules (ADR 0013 explains why it wasn't
  tightened), so these documents insert cleanly;
- the API only validates what clients send; it reads stored members as they are;
- a **data-quality report** before cutover lists them by `legacyId` (never by name or email:
  personal data), so the business decides whether to correct them.

### The one blocking conflict: emails differing only by case

Legacy's unique constraint was case-sensitive, so `jane@x.com` and `JANE@x.com` can both exist.
Lower-cased, they collide on the unique `email` index. Find them before the migration and have
someone decide which member keeps the address:

```sql
SELECT lower(email) AS address, count(*) AS members
FROM member
GROUP BY lower(email)
HAVING count(*) > 1;
```

`_0006` is the safety net for data already in MongoDB: it fails with a count only, and runs again
once the clash is resolved.

## Order of operations (snapshot with a maintenance window)

1. **Pre-checks** on legacy: email case conflicts (above), data-quality report, row count.
2. **Start the new app once against the empty target database.** Flamingock creates the
   collection, validator and indexes. (Bulk loading into an indexed collection is slower than
   indexing afterwards; at this size it doesn't matter, at millions of rows build the secondary
   indexes after the load.)
3. **Freeze writes** on legacy (maintenance page, or the database user made read-only).
4. **Copy** with the transform spec. MongoDB's tool for this is **Relational Migrator**: map the
   `member` table to `members`, rename the columns, map `id` to `legacyId`, and run a
   **snapshot** job. Lower-casing the email needs a calculated field in the mapping. A custom job
   (Spring Batch or a script writing in batches of ~1,000) is the alternative.
5. **Reconcile** (next section). Any unexplained difference stops the cutover.
6. **Switch traffic** to the new app and watch error rates and latency (Phase 7: cutover runbook).
7. **Keep legacy read-only and intact** until the new app has run cleanly for an agreed period.
   Until the first write lands in MongoDB, rolling back is just switching traffic back.

Do a **timed dry run** of steps 2 to 5 on a production-sized copy first: it sizes the
maintenance window and tests the reconciliation before it matters.

## Reconciliation

| Check | How | Passes when |
|---|---|---|
| Counts | `SELECT count(*) FROM member` vs `db.members.countDocuments({legacyId: {$exists: true}})` | Equal |
| Every row arrived once | Every legacy `id` has exactly one document with that `legacyId` (the unique index guarantees "at most one") | No id missing |
| Field by field | For each `id` / `legacyId` pair, compare `name`, `phone_number` exactly and `email` after lower-casing | No differences |
| At scale | Hash the normalized fields per record, compare hashes in `id` ranges, deep-compare only ranges that differ, plus a random sample | Ranges equal |
| Schema | Flamingock audit shows every change `APPLIED`; `getIndexes()` lists the expected indexes | All present |
| Queries | `explain()` on the list sort and the id lookup use the indexes, no in-memory `SORT` | Index scans only |
| The app | Contract suite and a smoke test against the migrated environment | Green |

Relational Migrator (1.18+) has **built-in data verification**: count check, then a field-by-field
comparison matched by primary key. Know its limits: it skips ObjectId-typed fields and calculated
fields (so the lower-cased email isn't checked by it), and it assumes the source doesn't change
during the job, which is why writes are frozen first.

## At scale: a larger codebase and real traffic

The same steps, applied **one domain at a time** (strangler fig): a gateway routes each domain's
endpoints to its new service when that domain's data has moved, and the legacy system shrinks
until it can be switched off. Per domain, pick the copy strategy by how long it can stop writing:

| Strategy | Downtime | Use when | Notes |
|---|---|---|---|
| **Snapshot** (above) | The copy + reconcile window | The domain fits in a maintenance window | Relational Migrator is **snapshot only**: its continuous (CDC) mode was removed from the tool in 1.15 and is offered through MongoDB's consulting engagement instead |
| **Snapshot + CDC catch-up** | Seconds to minutes | The domain must stay online | Snapshot while live, then stream changes from the database log (Debezium → Kafka → MongoDB Kafka sink connector) until in sync; short freeze to cut over. On PostgreSQL this needs logical replication (`wal_level=logical`) |
| **Dual write + backfill** | None | You own the app and can't run CDC infrastructure | Write through an **outbox** table, not two direct writes, or a failure between them leaves the stores inconsistent |
| **Parallel run (shadow reads)** | None | Before flipping any of the above | Mirror read traffic to the new service and compare responses; legacy stays authoritative |
| **Lazy migration** | None | Huge datasets with a small hot subset | Read misses fall back to legacy and copy the record; a sweeper moves the long tail |

**Rollback gets harder once writes land in MongoDB.** Before that, switching traffic back is
enough. After it, legacy is missing the new writes: either replay them into legacy (CDC in the
other direction, MongoDB change streams → SQL), or accept a forward-only migration once the new
system has proven itself. Decide which before cutover, and state the rollback trigger (for
example, error rate or p95 latency above the SLO for 10 minutes).
