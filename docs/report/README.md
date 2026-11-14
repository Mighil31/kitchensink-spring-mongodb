# Migration scorecard

`index.html` is a static page comparing the legacy app and the migrated app: contract parity,
performance against the SLO, and intentional changes. Open it in a browser. It is built from the
files in `data/` by `scripts/scorecard.py` (Python 3.12+, standard library only).

```bash
# 1. Run the contract suite against a target, then store the run
mvn -pl contract-tests verify -Pcontract                # legacy, defaults
python3 scripts/scorecard.py save-contract --name legacy --label "JBoss EAP 8.1 (legacy)"

# 2. Rebuild the page
python3 scripts/scorecard.py build
```

Sections with no data yet show as pending.

## Data files

| File | Written by | Contents |
|---|---|---|
| `data/contract-legacy/` | `save-contract --name legacy` | Failsafe XML of the legacy run + `meta.json`. Frozen: legacy doesn't change |
| `data/contract-v2/` | `save-contract --name v2` | Same, for the new app |
| `data/perf-legacy.json`, `data/perf-v2.json` | Phase 1b (Gatling results) | See below |
| `data/deviations.json` | By hand | Intentional behaviour changes; set `verifiedBy` to the new-app test that proves each one |

### Performance file format

```json
{
  "label": "JBoss EAP 8.1 (legacy)",
  "date": "2026-11-20",
  "slo": { "simulation": "MixedSloSimulation", "rps": 50, "p95Ms": 300, "errorRatePct": 1.0 },
  "kneeRps": 420,
  "simulations": [
    { "name": "MixedSloSimulation", "rps": 50, "requests": 15000,
      "p50Ms": 4, "p95Ms": 12, "p99Ms": 30, "errorRatePct": 0.0 }
  ]
}
```

`slo` is optional on the legacy file (the baseline is measured before the SLO is set). The SLO
verdict compares the named simulation's `p95Ms` and `errorRatePct` with the limits.
