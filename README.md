# kitchensink: Spring Boot + MongoDB

Migration of the JBoss EAP [kitchensink quickstart](https://github.com/jboss-developer/jboss-eap-quickstarts/tree/8.1.x/kitchensink)
to Spring Boot (Java 21) and MongoDB.

> Work in progress. Phase 1: contract (parity) tests against the legacy app.

## Layout

| Path | What |
|---|---|
| `contract-tests/` | Black-box HTTP suite capturing the legacy API's core behaviour. Runs against legacy now and against the new app later; only config changes. |

## Running the contract tests

Prerequisites: Java 21, Maven, the target app running.

```bash
# Against legacy JBoss EAP (default base URL)
mvn verify

# Against the new app (HTTP Basic; password from env only)
CONTRACT_PASSWORD=... mvn verify -Dcontract.baseUrl=http://localhost:8081/rest \
    -Dcontract.format=v2 -Dcontract.user=api
```

Write a Markdown summary of the last run (needs Python 3):

```bash
python3 scripts/contract_report.py --target "<app name and version>" --out docs/contract/<name>.md
```

Legacy baseline: [`docs/contract/legacy-baseline.md`](docs/contract/legacy-baseline.md).

The suite asserts only behaviour both apps must share. Intentional changes (bug fixes,
new formats) are tested in the new app and listed in `docs/` (coming).
