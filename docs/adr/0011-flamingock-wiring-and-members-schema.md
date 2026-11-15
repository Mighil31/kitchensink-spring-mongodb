# 0011. Flamingock on the plain driver; `members` schema details

**Status:** Accepted (2026-11-14). Refines 0005 and 0006; supersedes the earlier plan (Design
Decisions §5) not to pre-create the collection.

## Context

Phase 3 implements ADRs 0005 and 0006. Four details were open.

1. **Which Flamingock MongoDB adapter.** Flamingock 1.4.5 states Spring Boot 2.x–4.x support, but
   its Spring Data adapter is documented for Spring Data MongoDB 3.1–4.x. Boot 4.1.2 ships Spring
   Data MongoDB **5.1.2**: undocumented, and a major-version API change would fail at runtime, not
   compile time. Its plain-driver adapter needs only the MongoDB Java driver (Boot ships 5.8.1;
   Flamingock documents 5.x).
2. **When migrations run.** Flamingock's default Spring runner is an `ApplicationRunner`, which runs
   after the web server has opened its port.
3. **How strict the `$jsonSchema` validator is.** Legacy's Bean Validation is looser than it reads:
   `@Digits` on a String accepts `+123456789`, `1.23456789E9` and non-ASCII digits (gap B18), and
   the name pattern only bans ASCII 0-9 (B19).
4. **How the email index allows the case-insensitivity fix** (gap B2, Phase 5).

## Decision

1. **Plain-driver adapter** (`flamingock-mongodb-sync-targetsystem` + `-auditstore`) on Boot's
   `MongoClient`; changes receive a `MongoDatabase`. Only the Spring Boot integration and these two
   artifacts are used, not `flamingock-community`, which also pulls Couchbase, DynamoDB, SQL and
   Spring Data adapters. Spring Data stays the app's data access; only migrations bypass it.
2. **`flamingock.management-mode: INITIALIZING_BEAN`**: migrations finish during context startup,
   before the port opens. (The docs' `runnerType` property no longer exists in 1.4.5.)
3. **The validator is never stricter than the app layer.** Required `name`, `email`, `phoneNumber`;
   `name` 1–25 characters, no ASCII digits; `email` a non-empty string; `phoneNumber` 10–12
   characters, no character rule; `legacyId` a long. A stricter validator would turn requests the
   API accepts into write-time 500s. It tightens with the B18/B19 fixes, as a new change.
4. **Plain unique index on `email`, no collation.** Phase 5 stores emails trimmed and lower-cased,
   plus a backfill change; the same index then enforces case-insensitive uniqueness. A collation
   index would only serve queries passing the same collation (see 0009).

Changes, in order: `_0001__CreateMembersCollection` (with validator), `_0002__MembersEmailUniqueIndex`,
`_0003__MembersNameIndex`, `_0004__MembersLegacyIdIndex` (partial on `legacyId` existing),
`_0005__SeedJohnSmith` (`@Profile("dev")`, `legacyId: 0`). One index per change; all
`transactional = false` (DDL; compose runs a standalone `mongod` without transactions); each has a
`@Rollback`.

## Consequences

- The collection is created explicitly, because the validator is attached at creation. Doing it in
  one step lets the app's `readWrite` user run every change; `collMod` would need `dbAdmin`.
- Migrations don't depend on Flamingock's Spring Data compatibility, at the cost of using the
  driver API in change classes. They use string literals, not the `Member` class, so editing the
  model can't change a migration that has already run.
- Verified: changes run before Tomcat starts; a second boot applies none; the compose stack's
  `readWrite` user applies all five; without the `dev` profile only four run.
- Flamingock's processor warns that it supports source level 8 against our 21. Harmless today;
  watch for it in upgrades.
