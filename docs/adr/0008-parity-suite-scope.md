# 0008. The contract suite asserts only behaviour both apps share

**Status:** Accepted (2026-11-14)

## Context

The black-box HTTP suite in `contract-tests/` runs against legacy and the new app. Legacy has
behaviour to keep and bugs to fix. Asserting both, per app, would double the suite and blur
"must not change" with "spec for the fix".

## Decision

| Kind of behaviour | Where it is tested |
|---|---|
| Core behaviour, unchanged | Contract suite: one test, same expectation on both apps |
| Core behaviour in a new format (error body, 200 vs 201, id shape) | Contract suite, reading responses through a per-target format helper that only extracts, never decides |
| Bug fixes | The new app's own tests, next to the fix |
| Legacy bugs as they are | Gap analysis evidence; not re-asserted |

## Consequences

- A contract failure on the new app always means a regression.
- Every intentional change is listed with its ADR on the migration scorecard.
- The "before" of a fixed bug isn't re-checked automatically. Acceptable: legacy is frozen.
- LLM-generated edge cases that turned out to be legacy bugs (B18, B19) are tagged in the CSV and
  move out of the suite if the rule is fixed.
