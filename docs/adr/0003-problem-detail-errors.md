# 0003. Errors use RFC 9457 ProblemDetail

**Status:** Accepted (2026-11-14)

## Context

Legacy has a different error shape for every case, verified against the running app:

| Case | Status | Body |
|---|---|---|
| Validation failure | 400 | `{"name": "size must be between 1 and 25", ...}` |
| Duplicate email | 409 | `{"email": "Email taken"}` |
| Unknown id | 404 | empty |
| `null` body | 400 | `{"error": "HV000116: ..."}` (Hibernate Validator internals) |
| Malformed JSON | **500** | RESTEasy / JSON-B internals |

Clients can't tell a field error from a general one, nothing is machine-readable, internals leak,
and a client mistake returns 500.

## Decision

Every API error is `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail`
and `instance`. Validation errors add an `errors` map of field to message, so the per-field
information legacy clients relied on is kept. Internals are logged with a trace id, never returned.

Mapping, in one `@RestControllerAdvice`: validation → 400 · unreadable body → 400 ·
duplicate email (unique index) → 409 · unknown member → 404 · anything else → 500 with a generic
`detail`.

## Consequences

- **Intentional API change** (no in-repo REST consumers: the legacy UI calls the service directly).
- Fixes legacy bugs B5, B6 and B17 (500s and leaked internals).
- Still open: whether unknown JSON fields are rejected (B10) and whether a field that breaks two
  rules reports both messages (B16).
- If the contract had to stay stable, the alternative is the legacy shape on `/rest/**` and
  ProblemDetail on a versioned path.
