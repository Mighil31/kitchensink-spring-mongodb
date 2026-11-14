# 0010. Judge performance against an absolute SLO

**Status:** Proposed (2026-11-14). The load level is still to be decided.

## Context

The legacy baseline (Gatling, `perf/results/legacy/`): at 50 req/s, p95 1 ms and 0 errors for
registrations, lookups and an 80/20 mix; no knee up to 6,400 req/s, where Gatling and the server
were sharing one laptop.

Those numbers come from in-memory H2 inside the server's JVM with no authentication. The new app
adds a network hop to MongoDB and a bcrypt check on every API request (ADR 0007), so "no slower
than legacy" would fail for structural reasons, not regressions.

## Decision

- Pass/fail is an **absolute, user-facing SLO** on `MixedSloSimulation`: **p95 ≤ 300 ms and
  errors ≤ 1%**.
- The difference from legacy is reported as context, explained by attribution runs (the new app
  with and without auth isolates bcrypt's cost).
- **Open:** the SLO load level. The original plan, ~50% of legacy's knee, would mean 3,200+ req/s,
  unrealistic for a sign-up form. Proposal: 50 req/s steady, plus a headroom check at a few
  hundred req/s.

## Consequences

- The SLO becomes Gatling assertions on the mixed simulation: the performance gate for the new app.
- A fair like-for-like comparison would need legacy on a networked database (e.g. Postgres in
  Docker). Not built; stated as a caveat.
- Gatling over k6: one language and build for the team, HTML reports; the JVM load generator uses
  more CPU on a shared machine.
