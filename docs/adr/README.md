# Architecture Decision Records

One file per significant decision: the context, what was decided, and what it costs.
Accepted ADRs are not edited; a changed decision gets a new ADR that supersedes the old one.

| # | Decision | Status |
|---|---|---|
| [0001](0001-spring-boot-over-quarkus.md) | Target Spring Boot (not Quarkus) on Java 21 | Accepted |
| [0002](0002-objectid-ids-with-legacy-id.md) | Member ids become MongoDB ObjectIds; migrated records keep `legacyId` | Accepted |
| [0003](0003-problem-detail-errors.md) | Errors use RFC 9457 ProblemDetail with an `errors` map | Accepted |
| [0004](0004-thymeleaf-ui.md) | Replace JSF with Spring MVC + Thymeleaf | Accepted |
| [0005](0005-schema-and-seed-migrations.md) | Manage indexes, validators and seed data as versioned migrations | Accepted |
| [0006](0006-flamingock-for-migrations.md) | Use Flamingock (not Mongock) for MongoDB migrations | Accepted |
| [0007](0007-http-basic-on-rest-api.md) | HTTP Basic on all of `/rest/**`; UI stays public | Accepted |
| [0008](0008-parity-suite-scope.md) | The contract suite asserts only behaviour both apps share | Accepted |
| [0009](0009-keep-legacy-name-sort-order.md) | Keep legacy's case-sensitive name order | Accepted |
| [0010](0010-performance-slo.md) | Judge performance against an absolute SLO, not legacy's numbers | Proposed |
| [0011](0011-flamingock-wiring-and-members-schema.md) | Flamingock on the plain driver; `members` validator and index details | Accepted |

Template: Context · Decision · Consequences (and Options where the trade-off matters).
Intentional behaviour changes are listed with their ADR on the [migration scorecard](../report/index.html).
