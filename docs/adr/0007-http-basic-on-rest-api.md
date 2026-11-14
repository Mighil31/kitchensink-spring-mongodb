# 0007. HTTP Basic on the REST API; UI stays public

**Status:** Accepted (2026-11-14)

## Context

Legacy has no security at all: no authentication, no security headers, session cookie without
`HttpOnly`/`Secure`/`SameSite`, and CSRF protection only by accident. Open sign-up and a public
member list are part of the original design. The brief requires security.

Options: (A) hardening only, everything public · (B) protect the API, keep the UI public ·
(C) an admin role for member data.

## Decision

**Option B.** Two `SecurityFilterChain`s:

- `/rest/**`: HTTP Basic for every endpoint (reads and writes), stateless, CSRF off (no cookies),
  401/403 as ProblemDetail with `WWW-Authenticate`. One API-client user from environment variables,
  bcrypt-encoded.
- Everything else (UI): public, CSRF on.
- Both: security headers including a strict CSP, `Referrer-Policy`, `Permissions-Policy`; cookies
  `HttpOnly` + `SameSite=Lax` (+ `Secure` in prod); no internals in error responses; secrets only
  from the environment; Actuator exposes `health` and `info` only.

## Consequences

- **Intentional API change:** unauthenticated API calls get 401.
- bcrypt on every API request costs latency; the performance SLO (ADR 0010) has to account for it,
  and a with/without-auth run measures it.
- Next steps, out of scope: OAuth2/OIDC instead of Basic, real user accounts and roles, rate
  limiting on sign-up, field-level encryption for PII.
