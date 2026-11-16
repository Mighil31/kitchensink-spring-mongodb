# Gap analysis

What was wrong with, or missing from, legacy kitchensink, and what happened to each item in the
migration. Found in Phase 0 by reading every legacy class and sending real requests to the running
JBoss app; a few were found later by the contract suite's generated edge cases (B18, B19).

- **67 gaps** in 8 groups. IDs (B1, S7, …) are the ones used in commits, tests, ADRs and the
  [migration scorecard](report/index.html).
- **Priority:** P0 = must fix in the migration, P1 = should, P2 = nice to have or mention only.
- **Status:** *Fixed* (in the new app, with a test where it changes behaviour), *Kept* (deliberately
  the same as legacy), *Not done* (out of scope; a next step).
- The 19 that change what an API or UI client sees are on the scorecard with the test that proves each.

## Correctness bugs

| ID | Gap | P | Status |
|---|---|---|---|
| B1 | Two registrations with the same email at once: the loser gets a 400 with transaction-manager text | P0 | Fixed: unique index decides, loser gets 409 |
| B2 | Email uniqueness is case-sensitive: `JOHN@x.com` accepted next to `john@x.com` | P0 | Fixed: emails stored lower-case; existing data migrated (`_0006`) |
| B3 | A name of only spaces is accepted | P0 | Fixed |
| B4 | A client can send an `id` (400 with a Hibernate message) | P0 | Fixed: the request has no `id` field (ADR 0013) |
| B5 | Malformed JSON gives a 500 with RESTEasy / JSON-B internals | P0 | Fixed: 400 ProblemDetail (ADR 0003) |
| B6 | A `null` body gives a 400 with Hibernate Validator's code `HV000116` | P0 | Fixed: 400 ProblemDetail |
| B7 | The form shows the database's exception text for a taken email | P0 | Fixed: "Email taken" next to the field |
| B8 | The "member registered" CDI event fires before the transaction commits | P1 | Fixed: event dropped, the page reloads the list (ADR 0012) |
| B9 | UI messages use the first user's locale for everyone (fixed at class load) | P1 | Fixed: per-request locale (`Accept-Language`) |
| B10 | Unknown JSON fields are silently ignored | P2 | Kept: tolerant reader (ADR 0013) |
| B11 | The name rule's message says "letters and spaces", but only digits are banned: `<b>bold</b>` accepted | P1 | Fixed: letters, spaces, `. ' -`; message matches (ADR 0013) |
| B12 | `@Email` accepts `a@b` (no top-level domain) | P2 | Fixed: the domain needs a dot (ADR 0014). The database validator still allows it |
| B13 | Phone numbers: 10–12 digits, no country code or normalisation | P2 | Kept; E.164 is a next step |
| B14 | Refreshing after submitting the form registers again | P1 | Fixed: Post/Redirect/Get |
| B15 | Legacy's integration test fails on a second run (same data) | P1 | Fixed: the contract suite uses unique data per run |
| B16 | A field breaking two rules gets a random one of the two messages | P2 | Fixed: one message, fixed order (ADR 0013) |
| B17 | A JSON array body gives a 500 with internals | P0 | Fixed: 400 ProblemDetail |
| B18 | Phone numbers like `+123456789`, `1.23456789E9` or Arabic-Indic digits are accepted | P1 | Fixed: ASCII digits only (ADR 0013). The database validator still allows them (ADR 0013) |
| B19 | The name rule only bans ASCII digits: `Ana١`, `Ana１` accepted | P2 | Fixed with B11 |

## Security

| ID | Gap | P | Status |
|---|---|---|---|
| S1 | No authentication: anyone can register | P2 | Kept for the form (open sign-up); the API needs credentials (ADR 0007) |
| S2 | The member list (email, phone) is public | P2 | Kept on the page by design; the API needs credentials |
| S3 | Sequential numeric ids are easy to guess | P0 | Fixed: ObjectId ids (ADR 0002) plus authentication |
| S4 | Error responses leak framework and database internals | P0 | Fixed: generic ProblemDetail; details only in the log, with a request id |
| S5 | Mass assignment (see B4) | P0 | Fixed: request objects without `id` |
| S6 | Database credentials hard-coded in the WAR | P0 | Fixed: from the environment; MongoDB with auth; app user limited to read and write. A separate migration user is a next step |
| S7 | CSRF protection is accidental (JSF ViewState), not designed | P0 | Fixed: CSRF token on the form; the API is stateless with header auth |
| S8 | No security headers | P0 | Fixed: Spring Security defaults plus CSP, Referrer-Policy, Permissions-Policy |
| S8b | Session cookie without `HttpOnly`, `Secure`, `SameSite` | P0 | Fixed: `HttpOnly`, `SameSite=Lax`; `Secure` in the `prod` profile |
| S9 | Stored HTML in names (escaped by Facelets, so safe) | P1 | Kept safe: Thymeleaf escapes too; tested |
| S10 | No rate limiting on registration | P2 | Not done |
| S11 | No HTTPS configuration | P2 | TLS at the proxy; the `prod` profile trusts it and sends HSTS |
| S12 | Personal data in logs (the member's name) | P1 | Fixed: logs the id |
| S13 | No dependency scanning | P1 | Fixed: Dependabot |
| S14 | No CORS policy | P2 | Not needed yet: no browser client on another origin (browsers block it by default) |

## API design

| ID | Gap | P | Status |
|---|---|---|---|
| A1 | Create returns 200 with an empty body | P1 | Fixed: 201, `Location`, the member |
| A2 | No consistent error format | P0 | Fixed: RFC 9457 ProblemDetail (ADR 0003) |
| A3 | No paging: the list returns everything | P1 | Fixed: optional `?page=&size=` (ADR 0013) |
| A4 | Only create and read; no update or delete | P2 | Kept (parity) |
| A5 | No API documentation | P1 | Not done (OpenAPI is a next step); the Bruno collection covers every endpoint |
| A6 | No versioning strategy | P2 | Not done |
| A7 | The JPA entity is the API contract | P0 | Fixed: request and response objects |
| A8 | No idempotency for retried POSTs | P2 | Not done; the unique email already makes a retry a 409 |

## Data and persistence

| ID | Gap | P | Status |
|---|---|---|---|
| D1 | In-memory database, schema recreated on every start, no migrations | P0 | Fixed: MongoDB, versioned migrations (ADRs 0005, 0006, 0011) |
| D2 | No audit fields (created / updated at, by whom) | P1 | Not done |
| D3 | No optimistic locking | P2 | Not needed yet: members are never updated |
| D4 | The list's sort by name has no index; ordering is binary, case-sensitive | P1 | Fixed: `{name, _id}` index; legacy's order kept (ADR 0009) |
| D5 | Exceptions used for control flow (`NoResultException`) | P1 | Fixed: `Optional` |
| D6 | No backup, retention or erasure story | P2 | Not done |

## Observability and operations

| ID | Gap | P | Status |
|---|---|---|---|
| O1 | No health or readiness endpoints | P1 | Fixed: `/livez`, `/readyz` (includes MongoDB) |
| O2 | No metrics | P2 | Fixed: Prometheus on an internal port; optional Grafana dashboard |
| O3 | `java.util.logging`, string-built messages, no correlation id | P1 | Fixed: SLF4J, request id on every log line, JSON logs in `prod` |
| O4 | Configuration baked into the WAR | P0 | Fixed: `application.yml`, profiles, environment variables |
| O5 | No container setup for local runs | P1 | Fixed: Dockerfile and Docker Compose |
| O6 | No CI | P1 | Fixed: GitHub Actions (build, tests, contract suite, scorecard) |
| O7 | No graceful shutdown | P2 | Fixed: `server.shutdown=graceful` |

## Testing

| ID | Gap | P | Status |
|---|---|---|---|
| T1 | No unit tests | P0 | Fixed: unit, web-slice and Testcontainers tests |
| T2 | Two happy-path integration tests that need a running server | P0 | Fixed: the black-box contract suite, run against both apps |
| T3 | No negative, concurrency or security tests | P0 | Fixed: validation, 404/409, the duplicate-email race, 401/403, CSRF |
| T4 | No coverage or quality gate | P1 | Fixed: coverage gate and formatting check in the build. Mutation testing not done |

## UI

| ID | Gap | P | Status |
|---|---|---|---|
| U1 | No Post/Redirect/Get (see B14) | P1 | Fixed |
| U2 | Raw exception text shown to users (see B7) | P0 | Fixed |
| U3 | No client-side validation hints | P2 | Not done: the server validates |
| U4 | Accessibility: table layout, errors not linked to their inputs | P2 | Partly: semantic form, the success message is announced; errors aren't linked to inputs yet |
| U5 | Not responsive; JBoss branding | P1 | Fixed: responsive CSS, branding removed |
| U6 | English, French and Spanish texts | P1 | Kept |

## Platform

| ID | Gap | P | Status |
|---|---|---|---|
| X1 | Compiled for Java 17 | P0 | Fixed: Java 21 |
| X2 | Needs an application server (EAP, Galleon provisioning) | P0 | Fixed: Spring Boot executable jar |
