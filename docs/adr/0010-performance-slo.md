# 0010. Judge performance against an absolute SLO

**Status:** Accepted (2026-11-15). Proposed 2026-11-14; load level decided 2026-11-15.

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
- **Load level: 50 req/s steady**, the rate legacy's measured runs used, so the two compare
  directly. The original plan, ~50% of legacy's knee, would have meant 3,200+ req/s, unrealistic
  for a sign-up form. Headroom above it is shown by the knee run (rising levels until the SLO
  breaks), not judged.
- **Attribution run:** the same app with the API password configured as `{noop}...`, so every
  request is still authenticated, only without bcrypt. No switch that turns security off exists
  in the shipped app.

## Consequences

- **Full runs (2026-11-15), all on one 12-thread machine:** the new app **meets the SLO**: p95
  49 ms at 50 req/s with 0% errors (median of 3 runs, every simulation), and it holds the SLO up to
  200 req/s (breaks before 400). The same app authenticated without bcrypt (`{noop}`): p95 1 ms and
  no break up to 6,400 req/s, like legacy. bcrypt on every request is effectively the whole cost.
  A Grafana run (compose profile `observability`) shows CPU at about 95% at the bcrypt ceiling.
- First quick runs (10–20 req/s, 2026-11-15): p95 about 50 ms with bcrypt, about 5 ms without.
  bcrypt at its default cost (10) is roughly 45 ms of CPU per API request, which caps the API at
  about (CPU cores ÷ 0.045 s) requests per second however fast MongoDB is. Options if that matters:
  cache successful credential checks briefly, lower the cost factor, or move to tokens (OAuth2/JWT,
  ADR 0007 next steps), where each request checks a signature instead of a password hash.
- Found while setting up the attribution run: Spring Security re-hashes a password stored in a
  non-preferred encoding after a successful login, if the user store allows it. That turned `{noop}`
  into bcrypt on the first request. The API user store now exposes only the lookup.

- The SLO becomes Gatling assertions on the mixed simulation: the performance gate for the new app.
- A fair like-for-like comparison would need legacy on a networked database (e.g. Postgres in
  Docker). Not built; stated as a caveat.
- Gatling over k6: one language and build for the team, HTML reports; the JVM load generator uses
  more CPU on a shared machine.
