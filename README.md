# kitchensink: Spring Boot + MongoDB

Migration of the JBoss EAP [kitchensink quickstart](https://github.com/jboss-developer/jboss-eap-quickstarts/tree/8.1.x/kitchensink)
to Spring Boot (Java 21) and MongoDB.

> Work in progress. Phase 1 (contract baseline) is done; Phase 2 (app skeleton, local stack, CI) is in progress.

## Layout

| Path | What |
|---|---|
| `app/` | The Spring Boot 4 application (Java 21, Spring Data MongoDB). |
| `compose.yaml`, `docker/` | Local stack: MongoDB with auth + the app. |
| `contract-tests/` | Black-box HTTP suite capturing the legacy API's core behaviour. Runs against legacy now and against the new app later; only config changes. |
| `docs/adr/` | Architecture Decision Records: one page per significant decision. |
| `docs/report/` | **Migration scorecard**: one page comparing legacy and the new app (contract parity, performance vs SLO, intentional changes). |
| `perf/` | Gatling performance tests (standalone Maven project, not part of `mvn verify`). |
| `scripts/scorecard.py` | Stores test runs and builds the scorecard page. |

## Running the app

Prerequisites: Java 21, Maven, Docker.

```bash
# Whole stack in Docker
cp .env.example .env              # then change the passwords
docker compose up --build         # http://localhost:8081/actuator/health

# Or just the app, with a throwaway MongoDB from Testcontainers
mvn -pl app spring-boot:test-run

# Build + unit and integration tests (what CI runs)
mvn verify
```

Both local options run with the `dev` profile, which seeds legacy's John Smith member. The schema
(collection, validator, indexes) comes from Flamingock migrations in
`app/src/main/java/.../migration/`, applied at startup; see ADRs 0005, 0006 and 0011.

## Running the contract tests

Prerequisites: Java 21, Maven, the target app running. The suite is opt-in (`-Pcontract`), so a
plain `mvn verify` doesn't need a running server.

```bash
# Against legacy JBoss EAP (default base URL)
mvn -pl contract-tests verify -Pcontract

# Against the new app (HTTP Basic; password from env only)
CONTRACT_PASSWORD=... mvn -pl contract-tests verify -Pcontract \
    -Dcontract.baseUrl=http://localhost:8081/rest -Dcontract.format=v2 -Dcontract.user=api
```

The suite asserts only behaviour both apps must share. Intentional changes (bug fixes,
new formats) are tested in the new app and listed on the scorecard.

## Migration scorecard

[`docs/report/index.html`](docs/report/index.html) (open in a browser). After a contract run,
store it and rebuild the page (Python 3.12+):

```bash
python3 scripts/scorecard.py save-contract --name legacy --label "JBoss EAP 8.1 (legacy)"   # or --name v2
python3 scripts/scorecard.py build
```

Data formats and the performance file layout: [`docs/report/README.md`](docs/report/README.md).

## Performance tests

Three Gatling simulations: registrations only, lookups only, and a mixed 80/20 load (the SLO run).
`perf/run.sh` runs each one as a discarded warm-up plus 3 measured runs, then finds the knee by
running the mixed load at 50, 100, 200, 400 and 800 req/s until p95 > 300 ms or errors > 1%.
A full run takes about 45 minutes; keep the machine otherwise idle.

```bash
perf/run.sh legacy                  # or: perf/run.sh legacy quick  (2-minute dry run)
python3 scripts/scorecard.py save-perf --name legacy --label "JBoss EAP 8.1 (legacy)"
python3 scripts/scorecard.py build

# New app
PERF_ARGS="-DbaseUrl=http://localhost:8081/rest -Duser=api" PERF_PASSWORD=... perf/run.sh v2
```
