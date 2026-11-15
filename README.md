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
| [`docs/data-migration.md`](docs/data-migration.md) | How member data would move from legacy to MongoDB (transform spec, reconciliation) and the at-scale strategy. |
| `docs/report/` | **Migration scorecard**: one page comparing legacy and the new app (contract parity, performance vs SLO, intentional changes). |
| `perf/` | Gatling performance tests (standalone Maven project, not part of `mvn verify`). |
| `scripts/scorecard.py` | Stores test runs and builds the scorecard page. |

## Running the app

Prerequisites: Java 21, Maven, Docker.

```bash
# Whole stack in Docker
cp .env.example .env              # then change the passwords (MongoDB and the API client)
docker compose up --build         # http://localhost:8081/actuator/health

# Or just the app, with a throwaway MongoDB from Testcontainers
mvn -pl app spring-boot:test-run

# Build + unit and integration tests (what CI runs)
mvn verify
```

Both local options run with the `dev` profile, which seeds legacy's John Smith member. The schema
(collection, validator, indexes) comes from Flamingock migrations in
`app/src/main/java/.../migration/`, applied at startup; see ADRs 0005, 0006 and 0011.

### Security (ADR 0007)

The registration page is public, as on legacy, with a CSRF token on its form. The REST API
(`/rest/**`, reads too) needs HTTP Basic as its one client:

| Run | Username / password |
|---|---|
| `docker compose up` | `API_USER` / `API_PASSWORD` from `.env` |
| `spring-boot:test-run` | `test-client` / `test-password` (test classpath only) |
| Anywhere else | env `KITCHENSINK_API_USERNAME` / `KITCHENSINK_API_PASSWORD`; the app won't start without them |

```bash
curl -u test-client:test-password http://localhost:8081/rest/members
```

The password is bcrypt-hashed at startup; `KITCHENSINK_API_PASSWORD` may also hold the hash itself
(`{bcrypt}$2a$...`), so the plain text never reaches the server. Without credentials the API answers
401 as a ProblemDetail. All responses carry a strict Content-Security-Policy, Referrer-Policy and
Permissions-Policy. The `prod` profile marks the session cookie `Secure` and trusts the TLS proxy's
`X-Forwarded-Proto`, so HSTS is sent.

## Trying the API by hand (Bruno)

[`bruno/`](bruno) is a [Bruno](https://www.usebruno.com/) collection: in Bruno, *Open Collection*
and pick that folder. It has the happy path (list, register, get by id) and every error the API
returns (401, 400, 404, 405, 406, 409, 415), each with tests and a note on the legacy behaviour it
replaces.

| Environment | For |
|---|---|
| Testcontainers run | `mvn -pl app spring-boot:test-run`; credentials included (test-only) |
| Docker compose | `docker compose up`; set the secret `apiPassword` to `API_PASSWORD` from `.env` (kept by Bruno on your machine, not in the repo) |
| Legacy JBoss | the original app on port 8080; no auth. Many tests fail there on purpose: they show what changed |

`GET /rest/members` returns every member, as legacy did; add `?page=0&size=20` (size up to 100)
for one page, with a `Link: <...>; rel="next"` header while more follow (ADR 0013).

Run **Members** before **Errors** (*Get member* uses the id *Register member* saved).

## Running the contract tests

Prerequisites: Java 21, Maven, the target app running. The suite is opt-in (`-Pcontract`), so a
plain `mvn verify` doesn't need a running server.

```bash
# Against legacy JBoss EAP (default base URL)
mvn -pl contract-tests verify -Pcontract

# Against the new app
mvn -pl contract-tests verify -Pcontract \
    -Dcontract.baseUrl=http://localhost:8081/rest -Dcontract.format=v2 \
    -Dcontract.user=test-client       # HTTP Basic user; the password comes from env CONTRACT_PASSWORD
```

The suite asserts only behaviour both apps must share. Intentional changes (bug fixes,
new formats) are tested in the new app and listed on the scorecard.

CI's `contract-v2` job runs the suite against the new app on every push and pull request (jar on a
fresh MongoDB, no dev profile, an API password generated for that run), so a parity regression
fails the build. It also checks that the API answers 401 without credentials. The scorecard's v2 column
is a committed snapshot: refresh it with `scorecard.py save-contract --name v2` after a run.

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
