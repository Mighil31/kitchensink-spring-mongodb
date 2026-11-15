# 0013. The remaining behaviour changes: ids, unknown fields, validation rules, paging

**Status:** Accepted (2026-11-15). Closes issue #12.

## Context

Six gaps were left open after Phase 5, each a choice between keeping legacy's behaviour and
changing it (Gap Analysis, scorecard "Intentional changes"):

| Gap | Legacy |
|---|---|
| B4 | A client can send an `id`; legacy answers 400 with a Hibernate message |
| B10 | Unknown JSON fields are silently ignored |
| B11, B19 | The name rule bans only ASCII 0-9, but its message says "letters and spaces": `<b>bold</b>`, emoji, `Ana١` are accepted |
| B18 | `@Digits` on a String parses it as a number: `+123456789`, `1.23456789E9`, Arabic-Indic digits are stored as phone numbers |
| B16 | A field breaking two rules reports one message, picked at random |
| A3 | `GET /members` returns every member, unbounded |

## Decision

- **B4: the client can't choose the id.** The request body has no `id` field (done in Phase 4);
  a sent `id` is ignored like any unknown field and the database generates one.
- **B10: keep ignoring unknown fields**, as legacy. Rejecting them would catch client typos, but
  would break every client that sends an extra field, in the middle of a migration. Tolerant reader.
- **B11, B19: names are letters of any script, spaces and `. ' ’ -`**
  (`[\p{L}\p{M} .'’-]*`), and the message says so: "must contain only letters, spaces, hyphens,
  apostrophes and periods" (Spanish and French too). The punctuation keeps O'Brien, Jean-Luc and
  St. John valid; `\p{M}` keeps accents typed as a separate combining mark. Only the plain space
  counts: tabs, newlines and non-breaking spaces are rejected.
- **B18: phone numbers are 10 to 12 ASCII digits.** A `[0-9]*` pattern is added and `@Digits`
  kept, so input legacy already rejected keeps legacy's message; the new message ("must contain
  only the digits 0-9") only appears for what legacy let through.
- **B16: one message per field, in a fixed order**: missing (`NotNull`, `NotEmpty`), then length
  (`Size`), then content (`Digits`, `Pattern`, `Email`); ties go to the alphabetically first
  message. The `errors` map keeps its field-to-string shape. Same rule for the API and the form.
- **A3: paging on request.** `?page=` (from 0) and/or `?size=` (1 to 100, default 20) return one
  page; without them the whole list comes back, as on legacy. The body is a plain array either
  way, so the response shape never changes; a `Link: <...>; rel="next"` header (RFC 8288) points to
  the next page while there is one. Out-of-range values are a 400 ProblemDetail. Members with the
  same name are ordered by id, so pages never repeat or skip anyone; migration `_0007` replaces the
  `name` index with `{name, _id}` so the sort stays index-backed.

## Consequences

- **Intentional API changes:** names and phone numbers legacy accepted are now 400, with a
  reworded name message. Existing data isn't touched: a stored member that breaks the new rules
  stays as it is (Phase 6 data migration reports them).
- The parity suite moved 7 cases out (they now differ by design) and accepts either app's wording
  of the name rule; those cases are tested in the new app instead. 44 shared cases remain.
- The database's `$jsonSchema` validator is **not** tightened: changing it needs `collMod`, which
  the app's `readWrite` user doesn't have, and granting `dbAdmin` to the app undoes least
  privilege. It stays looser than the app, which ADR 0011 allows. Tightening it belongs with a
  separate migration user (follow-up).
- Offset paging (`skip`) slows down on deep pages of a large collection. Keyset paging
  (`?after=<name,id>`) is the next step if clients page deep; the `{name, _id}` index already
  supports it.
- Rejecting unknown fields (B10) can still be turned on later per client version, if needed.
