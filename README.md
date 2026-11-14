# kitchensink: Spring Boot + MongoDB

Migration of the JBoss EAP [kitchensink quickstart](https://github.com/jboss-developer/jboss-eap-quickstarts/tree/8.1.x/kitchensink)
to Spring Boot (Java 21) and MongoDB.

> Work in progress. Phase 1: contract (parity) tests against the legacy app.

## Layout

| Path | What |
|---|---|
| `contract-tests/` | Black-box HTTP suite capturing the legacy API's core behaviour. Runs against legacy now and against the new app later; only config changes. |
| `docs/report/` | **Migration scorecard**: one page comparing legacy and the new app (contract parity, performance vs SLO, intentional changes). |
| `perf/` | Gatling performance tests (standalone Maven project, not part of `mvn verify`). |
| `scripts/scorecard.py` | Stores test runs and builds the scorecard page. |

## Running the contract tests

Prerequisites: Java 21, Maven, the target app running.

```bash
# Against legacy JBoss EAP (default base URL)
mvn verify

# Against the new app (HTTP Basic; password from env only)
CONTRACT_PASSWORD=... mvn verify -Dcontract.baseUrl=http://localhost:8081/rest \
    -Dcontract.format=v2 -Dcontract.user=api
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
