# 0014. Emails need a dotted domain

**Status:** Accepted (2026-11-16). Supersedes the "Kept" decision on gap B12.

## Context

`@Email` follows the RFC, which allows a domain without a dot: legacy accepts `jane@gmail` and
`a@b`. These are valid on an internal network but never on the public internet, where members
register. In practice they are typos (a missing `.com`).

## Decision

Reject them. A `@Pattern` next to `@Email` requires the domain to contain a dot and end in a label
of at least 2 characters. IP literals (`jane@[127.0.0.1]`) stay valid. The pattern uses `@Email`'s
own message, so a client sees "must be a well-formed email address" whichever rule fails, and
input legacy already rejected keeps legacy's message.

## Consequences

- **Intentional API change:** emails legacy accepted are now 400. Tested in the app
  (`MemberRestControllerTest.emailWithoutTopLevelDomainIs400`); the parity case moved to an email
  both apps accept, so the suite still has 44 shared cases.
- This only checks the address's shape. Whether it exists is only proven by a confirmation email.
- The database validator is not tightened, for the same reason as ADR 0013: existing and migrated
  members keep their stored email, and the data-quality report lists them.
