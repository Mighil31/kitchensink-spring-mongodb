# Cutover runbook

How production traffic would move from legacy kitchensink to the new app, and how to go back.
Phase 7 of the migration plan; the data steps are in [data-migration.md](data-migration.md).
Kitchensink has no production deployment, so this is the outline, not a script.

## Switching traffic

Clients reach the app through a gateway (nginx here). Switching is a config change and a reload,
so it takes seconds and reverses the same way:

```nginx
location /rest/ {
    proxy_pass http://legacy:8080/kitchensink/rest/;   # after cutover: http://new-app:8081/rest/;
}
location / {
    proxy_pass http://legacy:8080/kitchensink/;        # after cutover: http://new-app:8081/;
}
```

In a larger system the gateway moves one domain at a time (strangler fig); kitchensink has one.

## Before the day

- **API clients are ready.** The new API isn't a drop-in replacement: clients need credentials
  (ADR 0007) and must handle hex ids, 201 on create and ProblemDetail errors (full list on the
  migration scorecard).
- **New app deployed** and healthy, gateway not yet pointing at it.
- **Dry run** of the data copy and reconciliation on a copy of production, timed, to size the
  maintenance window.

## The day

1. **Freeze writes** on legacy (the gateway answers 503 to anything but reads).
2. **Copy the data** and **reconcile** it (data-migration.md). Differences stop the cutover.
3. **Smoke test** the new app: health, member count, one member lookup.
4. **Switch the gateway** to the new app and lift the freeze.
5. **Watch** error rate and latency for the next hours.

## Rollback

**When:** errors or latency outside the SLO (ADR 0010: p95 over 300 ms or more than 1% errors)
for several minutes, and no quick fix.

**How:**

- **Before any new write reached MongoDB:** switch the gateway back to legacy. Nothing is lost.
- **After new writes:** legacy is missing them, so copy them back first. Kitchensink never
  updates or deletes members, so that's only the new registrations (documents without a
  `legacyId`). A system with updates would need to stream changes back from MongoDB, which is
  why most migrations set a date after which they only go forward.

Once the new app has run cleanly for an agreed period, legacy is kept read-only for reference and
then switched off.
