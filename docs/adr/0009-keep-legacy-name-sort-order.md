# 0009. Keep legacy's case-sensitive name order

**Status:** Accepted (2026-11-14). Replaces an earlier, unrecorded plan to sort case-insensitively.

## Context

`GET /members` and the UI list members sorted by name. Measured on legacy (H2), the order is by
character code, so case-sensitive: `Alice < Bob < alice < bob < zed < Émile`. A case-insensitive
collation (`locale: en, strength: 2`) had been planned for the new app.

## Decision

Keep the legacy order. MongoDB without a collation also compares strings by binary value, so the
new app needs no collation, just a plain index on `name`.

## Consequences

- Sorting stays a parity behaviour, checked by the contract suite with mixed-case names.
- Changing the order would be a visible behaviour change nobody asked for.
- Edge case: H2 compares UTF-16 code units, MongoDB compares UTF-8 bytes. They can disagree for
  characters outside the Basic Multilingual Plane (e.g. emoji) against some symbols; normal names
  are unaffected.
