#!/usr/bin/env python3
"""Migration scorecard: one static page comparing legacy and the migrated app.

Commands:
    save-contract  Copy the last contract-test run (Failsafe XML) into docs/report/data/contract-<name>/
    build          Render docs/report/index.html from everything in docs/report/data/

    python3 scripts/scorecard.py save-contract --name legacy --label "JBoss EAP 8.1 (legacy)"
    python3 scripts/scorecard.py save-contract --name v2 --label "Spring Boot 4 + MongoDB"
    python3 scripts/scorecard.py build

Data layout and formats: docs/report/README.md. Python 3.12+, standard library only.
"""
import argparse
import datetime
import glob
import html
import json
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "docs", "report", "data")
OUT = os.path.join(ROOT, "docs", "report", "index.html")
REPORTS = os.path.join(ROOT, "contract-tests", "target", "failsafe-reports")
PACKAGE = "io.github.mighil31.kitchensink.contract."
TARGETS = (("legacy", "Legacy"), ("v2", "New app"))

PERF_CAVEATS = [
    "Legacy uses in-memory H2 inside the app server's JVM; the new app talks to MongoDB over the network.",
    "Legacy has no authentication; the new app checks HTTP Basic credentials (bcrypt) on every request.",
    "Legacy checks for a duplicate email with a separate query before inserting; the new app relies on a unique index.",
    "Same machine, same Docker resources, warm-up run discarded, median of three runs.",
]


# ---------- shared helpers ----------

def esc(value):
    return html.escape(str(value))


def git_head():
    try:
        return subprocess.check_output(["git", "-C", ROOT, "rev-parse", "--short", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def read_json(path):
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def badge(kind, text):
    return f'<span class="badge {kind}">{esc(text)}</span>'


# ---------- save-contract ----------

KEEP_PROPERTIES = re.compile(r"^(contract\.|java\.version$)")


def strip_private_properties(tree):
    """Failsafe records every JVM system property (user name, home paths, timezone...).
    Saved runs end up in a public repo, so keep only what the scorecard uses."""
    for properties in tree.getroot().iter("properties"):
        for prop in list(properties):
            if not KEEP_PROPERTIES.match(prop.get("name", "")):
                properties.remove(prop)

def save_contract(args):
    files = sorted(glob.glob(os.path.join(args.reports, "TEST-*.xml")))
    if not files:
        raise SystemExit(f"No Failsafe reports in {args.reports}; run mvn verify first")

    dest = os.path.join(DATA, f"contract-{args.name}")
    shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest)
    props = {}
    for path in files:
        tree = ET.parse(path)
        props.update({p.get("name"): p.get("value") for p in tree.getroot().iter("property")})
        strip_private_properties(tree)
        tree.write(os.path.join(dest, os.path.basename(path)), encoding="UTF-8", xml_declaration=True)

    meta = {
        "label": args.label,
        "date": datetime.date.today().isoformat(),
        "suiteCommit": git_head(),
        "baseUrl": props.get("contract.baseUrl"),
        "format": props.get("contract.format"),
        "java": props.get("java.version"),
    }
    with open(os.path.join(dest, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")
    print(f"Saved {len(files)} report files to {os.path.relpath(dest, ROOT)}")


# ---------- loading ----------

def outcome(case):
    for tag, kind, label in (("failure", "fail", "failed"), ("error", "fail", "error"), ("skipped", "skip", "skipped")):
        element = case.find(tag)
        if element is not None:
            detail = ((element.get("message") or "") + "\n\n" + (element.text or "")).strip()
            return {"kind": kind, "label": label, "detail": detail}
    return {"kind": "pass", "label": "passed", "detail": ""}


def load_contract(data_dir, name):
    """{'meta': {...}, 'results': {(suite, test): outcome}} or None if this target hasn't been run."""
    run_dir = os.path.join(data_dir, f"contract-{name}")
    files = sorted(glob.glob(os.path.join(run_dir, "TEST-*.xml")))
    if not files:
        return None
    results = {}
    for path in files:
        root = ET.parse(path).getroot()
        suite = root.get("name").removeprefix(PACKAGE)
        for case in root.iter("testcase"):
            results[(suite, case.get("name"))] = outcome(case)
    return {"meta": read_json(os.path.join(run_dir, "meta.json")) or {}, "results": results}


def split_name(name):
    """'invalidName_isRejected(String, Set) name "R2D2" -> 400' -> ('name "R2D2" -> 400', 'invalidName_isRejected')."""
    match = re.match(r"^(\w+)\([^)]*\) (.+)$", name)
    return (match[2], match[1]) if match else (name, None)


# ---------- sections ----------

def summary_tiles(contract, perf, deviations):
    tiles = []

    legacy = contract["legacy"]
    if legacy:
        res = legacy["results"].values()
        ok = sum(1 for r in res if r["kind"] == "pass")
        tiles.append(("Legacy baseline", f"{ok}/{len(res)}", "ok" if ok == len(res) else "bad",
                      "contract tests passing on the legacy app"))
    else:
        tiles.append(("Legacy baseline", "—", "pending", "not recorded yet"))

    v2 = contract["v2"]
    if v2 and legacy:
        required = [k for k, r in legacy["results"].items() if r["kind"] == "pass"]
        ok = sum(1 for k in required if v2["results"].get(k, {}).get("kind") == "pass")
        tiles.append(("Parity", f"{ok}/{len(required)}", "ok" if ok == len(required) else "bad",
                      "legacy behaviours the new app reproduces"))
    else:
        tiles.append(("Parity", "Pending", "pending", "new app not built yet (Phase 4)"))

    new_perf = perf.get("v2")
    if new_perf and slo_verdict(new_perf) is not None:
        met = slo_verdict(new_perf)
        tiles.append(("Performance SLO", "Met" if met else "Missed", "ok" if met else "bad",
                      "new app against the agreed SLO"))
    elif perf.get("legacy"):
        tiles.append(("Performance SLO", "Pending", "pending", "legacy baseline recorded; new app not measured"))
    else:
        tiles.append(("Performance SLO", "Pending", "pending", "baseline not measured yet (Phase 1b)"))

    items = (deviations or {}).get("items", [])
    decided = sum(1 for d in items if d.get("status") == "decided")
    tiles.append(("Intentional changes", str(len(items)), "neutral",
                  f"{decided} decided, {len(items) - decided} still open"))

    return '<div class="tiles">' + "".join(
        f'<div class="tile"><div class="tile-label">{esc(label)}</div>'
        f'<div class="tile-value {kind}">{esc(value)}</div><div class="tile-note">{esc(note)}</div></div>'
        for label, value, kind, note in tiles) + "</div>"


def run_meta(contract):
    rows = []
    for key, title in TARGETS:
        run = contract[key]
        if not run:
            rows.append(f"<tr><th>{esc(title)}</th><td colspan=\"4\" class=\"muted\">Not run yet</td></tr>")
            continue
        m = run["meta"]
        rows.append(
            f"<tr><th>{esc(title)}</th><td>{esc(m.get('label', '?'))}</td><td><code>{esc(m.get('baseUrl', '?'))}</code></td>"
            f"<td class=\"nowrap\">{esc(m.get('date', '?'))}</td><td><code>{esc(m.get('suiteCommit', '?'))}</code></td></tr>")
    return ('<div class="scroll"><table class="meta"><thead><tr><th></th><th>Target</th><th>Base URL</th>'
            '<th>Run on</th><th>Suite commit</th></tr></thead><tbody>' + "".join(rows) + "</tbody></table></div>")


def contract_cell(run, key):
    if run is None:
        return f'<td class="result">{badge("pending", "pending")}</td>'
    result = run["results"].get(key)
    if result is None:
        return f'<td class="result">{badge("pending", "not run")}</td>'
    detail = ""
    if result["kind"] == "fail" and result["detail"]:
        detail = f'<details><summary>Why</summary><pre>{esc(result["detail"])}</pre></details>'
    return f'<td class="result">{badge(result["kind"], result["label"])}{detail}</td>'


def contract_section(contract):
    keys = set()
    for key, _ in TARGETS:
        if contract[key]:
            keys.update(contract[key]["results"])
    if not keys:
        return '<p class="muted">No contract runs recorded yet.</p>'

    by_suite = {}
    for suite, test in sorted(keys):
        by_suite.setdefault(suite, []).append(test)

    blocks = []
    for suite, tests in by_suite.items():
        rows = []
        for test in tests:
            label, method = split_name(test)
            method_html = f'<div class="method">{esc(method)}</div>' if method else ""
            cells = "".join(contract_cell(contract[k], (suite, test)) for k, _ in TARGETS)
            rows.append(f'<tr><td class="test">{esc(label)}{method_html}</td>{cells}</tr>')
        blocks.append(
            f'<h3>{esc(suite)}</h3><table class="matrix"><thead><tr><th>Test</th>'
            + "".join(f'<th class="result">{esc(t)}</th>' for _, t in TARGETS)
            + "</tr></thead><tbody>" + "".join(rows) + "</tbody></table>")
    return "".join(blocks)


def slo_verdict(run):
    """True/False if the run declares an SLO and has the simulation it applies to, else None."""
    slo = run.get("slo")
    if not slo:
        return None
    sims = {s["name"]: s for s in run.get("simulations", [])}
    sim = sims.get(slo.get("simulation"))
    if sim is None:
        return None
    return sim["p95Ms"] <= slo["p95Ms"] and sim["errorRatePct"] <= slo["errorRatePct"]


def fmt(value, unit=""):
    if value is None:
        return "—"
    if isinstance(value, float):
        value = f"{value:.1f}" if value % 1 else f"{int(value)}"
    return f"{value}{unit}"


def perf_section(perf):
    if not any(perf.values()):
        return ('<p class="pending-note">Not measured yet. Phase 1b runs Gatling simulations against legacy to record '
                'a baseline, then sets the SLO from it. The new app is measured after Phase 5.</p>'
                + caveats_html())

    parts = []
    slo_source = perf.get("v2") or perf.get("legacy")
    slo = slo_source.get("slo") if slo_source else None
    if slo:
        parts.append(
            f'<p><strong>SLO</strong>: <code>{esc(slo.get("simulation", "?"))}</code> at {fmt(slo.get("rps"), " req/s")}, '
            f'p95 &le; {fmt(slo.get("p95Ms"), " ms")}, errors &le; {fmt(slo.get("errorRatePct"), "%")}</p>')

    names = []
    for key, _ in TARGETS:
        for sim in (perf.get(key) or {}).get("simulations", []):
            if sim["name"] not in names:
                names.append(sim["name"])

    rows = []
    for name in names:
        for i, (key, title) in enumerate(TARGETS):
            run = perf.get(key)
            sim = next((s for s in (run or {}).get("simulations", []) if s["name"] == name), None)
            first = f'<td rowspan="2" class="test"><code>{esc(name)}</code></td>' if i == 0 else ""
            if sim is None:
                rows.append(f'<tr>{first}<td>{esc(title)}</td><td colspan="6">{badge("pending", "pending")}</td></tr>')
                continue
            rows.append(
                f"<tr>{first}<td>{esc(title)}</td><td>{fmt(sim.get('rps'))}</td><td>{fmt(sim.get('p50Ms'))}</td>"
                f"<td>{fmt(sim.get('p95Ms'))}</td><td>{fmt(sim.get('p99Ms'))}</td>"
                f"<td>{fmt(sim.get('errorRatePct'), '%')}</td><td>{fmt(sim.get('requests'))}</td></tr>")
    parts.append(
        '<div class="scroll"><table class="perf"><thead><tr><th>Simulation</th><th>App</th><th>req/s</th>'
        '<th>p50 ms</th><th>p95 ms</th><th>p99 ms</th><th>Errors</th><th>Requests</th></tr></thead><tbody>'
        + "".join(rows) + "</tbody></table></div>")

    knees = [(title, (perf.get(key) or {}).get("kneeRps")) for key, title in TARGETS]
    if any(k for _, k in knees):
        parts.append("<p><strong>Knee</strong> (rate where the SLO breaks): "
                     + " · ".join(f"{esc(t)} {fmt(k, ' req/s')}" for t, k in knees) + "</p>")
    for key, title in TARGETS:
        verdict = slo_verdict(perf.get(key) or {})
        if verdict is not None:
            parts.append(f"<p>{esc(title)}: {badge('pass' if verdict else 'fail', 'SLO met' if verdict else 'SLO missed')}</p>")

    return "".join(parts) + caveats_html()


def caveats_html():
    return ('<details class="caveats"><summary>Why the numbers differ (read before comparing)</summary><ul>'
            + "".join(f"<li>{esc(c)}</li>" for c in PERF_CAVEATS) + "</ul></details>")


def deviations_section(deviations):
    items = (deviations or {}).get("items", [])
    if not items:
        return '<p class="muted">No intentional changes recorded.</p>'
    rows = []
    for d in items:
        status = badge("pass", "decided") if d.get("status") == "decided" else badge("skip", "open")
        proof = d.get("verifiedBy")
        proof_html = f"<code>{esc(proof)}</code>" if proof else badge("pending", "pending")
        rows.append(
            f'<tr><td class="nowrap"><code>{esc(d.get("id", ""))}</code></td><td class="test">{esc(d.get("change", ""))}</td>'
            f'<td>{esc(d.get("legacy", ""))}</td><td>{esc(d.get("new", ""))}</td>'
            f'<td>{esc(d.get("source", ""))}</td><td>{status}</td><td>{proof_html}</td></tr>')
    return ('<div class="scroll"><table class="deviations"><thead><tr><th>Id</th><th>Change</th><th>Legacy</th>'
            '<th>New app</th><th>Decision</th><th>Status</th><th>Proven by</th></tr></thead><tbody>'
            + "".join(rows) + "</tbody></table></div>")


# ---------- build ----------

CSS = """
:root {
  --bg: #f7f7f5; --card: #ffffff; --text: #1d1d1b; --muted: #6b6b66; --line: #e4e4df;
  --pass: #1f7a4d; --pass-bg: #e3f3ea; --fail: #b3261e; --fail-bg: #fbe6e4;
  --skip: #8a6d00; --skip-bg: #fbf1d0; --pend: #5b5b57; --pend-bg: #ececea;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #161615; --card: #1f1f1d; --text: #ececea; --muted: #a0a09a; --line: #33332f;
    --pass: #6fd3a0; --pass-bg: #173326; --fail: #ff8a80; --fail-bg: #3a1c1a;
    --skip: #e8c860; --skip-bg: #352c10; --pend: #b5b5ae; --pend-bg: #2c2c29;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width: 1000px; margin: 0 auto; padding: 32px 16px 64px; }
h1 { font-size: 1.6rem; margin: 0 0 4px; }
h2 { font-size: 1.2rem; margin: 0 0 4px; }
h3 { font-size: 0.95rem; margin: 20px 0 6px; color: var(--muted); font-weight: 600; }
.subtitle, .muted { color: var(--muted); }
.subtitle { margin: 0 0 24px; }
.lead { color: var(--muted); margin: 0 0 12px; }
section.card { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 20px; margin-bottom: 20px; }
.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 20px; }
.tile { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; }
.tile-label { color: var(--muted); font-size: 0.85rem; }
.tile-value { font-size: 1.7rem; font-weight: 700; line-height: 1.3; }
.tile-value.ok { color: var(--pass); } .tile-value.bad { color: var(--fail); } .tile-value.pending { color: var(--pend); }
.tile-note { color: var(--muted); font-size: 0.8rem; }
code { font: 0.88em ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
.scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-weight: 600; color: var(--muted); font-size: 0.85rem; padding: 6px 8px 6px 0; }
td { padding: 7px 8px 7px 0; border-top: 1px solid var(--line); vertical-align: top; }
.result { width: 1%; white-space: nowrap; }
.test { overflow-wrap: anywhere; }
.method { color: var(--muted); font-size: 0.82em; }
table.meta { min-width: 640px; }
table.perf { min-width: 600px; }
table.deviations { min-width: 820px; }
table.meta tbody th { color: var(--text); }
.nowrap, table.meta code, table.perf td { white-space: nowrap; }
.badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 0.78rem; font-weight: 600; white-space: nowrap; }
.badge.pass { color: var(--pass); background: var(--pass-bg); }
.badge.fail { color: var(--fail); background: var(--fail-bg); }
.badge.skip { color: var(--skip); background: var(--skip-bg); }
.badge.pending { color: var(--pend); background: var(--pend-bg); }
details { margin-top: 6px; }
summary { cursor: pointer; color: var(--muted); font-size: 0.9rem; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: 0.8rem ui-monospace, Menlo, monospace; background: var(--bg);
      border: 1px solid var(--line); border-radius: 6px; padding: 8px; max-height: 320px; overflow: auto; }
.pending-note { background: var(--pend-bg); border-radius: 8px; padding: 10px 12px; margin: 0 0 8px; }
.caveats ul { margin: 6px 0 0; padding-left: 20px; color: var(--muted); }
footer { color: var(--muted); font-size: 0.82rem; text-align: center; }
"""


def build(args):
    contract = {key: load_contract(args.data, key) for key, _ in TARGETS}
    perf = {key: read_json(os.path.join(args.data, f"perf-{key}.json")) for key, _ in TARGETS}
    deviations = read_json(os.path.join(args.data, "deviations.json"))

    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Migration Scorecard</title>
<style>{CSS}</style>
</head>
<body>
<main>
<h1>kitchensink migration scorecard</h1>
<p class="subtitle">JBoss EAP + H2 &rarr; Spring Boot + MongoDB. Generated {datetime.date.today().isoformat()} from commit <code>{esc(git_head())}</code>.</p>

{summary_tiles(contract, perf, deviations)}

<section class="card">
<h2>Contract parity</h2>
<p class="lead">The same black-box HTTP suite runs against both apps. It only asserts behaviour both must share; bug fixes are tested in the new app and listed under Intentional changes.</p>
{run_meta(contract)}
{contract_section(contract)}
</section>

<section class="card">
<h2>Performance</h2>
<p class="lead">Pass/fail is the SLO; the difference from legacy is context, not a target.</p>
{perf_section(perf)}
</section>

<section class="card">
<h2>Intentional changes</h2>
<p class="lead">Behaviour that differs from legacy on purpose. Each one traces to a decision and is proven by a test in the new app.</p>
{deviations_section(deviations)}
</section>

<footer>Built by <code>scripts/scorecard.py</code> from <code>docs/report/data/</code>.</footer>
</main>
</body>
</html>
"""
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(page)
    print(f"Wrote {os.path.relpath(args.out, ROOT) if args.out.startswith(ROOT) else args.out}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    save = sub.add_parser("save-contract", help="Store the last contract-test run")
    save.add_argument("--name", required=True, choices=[k for k, _ in TARGETS])
    save.add_argument("--label", required=True, help="Human-readable app name and version")
    save.add_argument("--reports", default=REPORTS)
    save.set_defaults(func=save_contract)

    bld = sub.add_parser("build", help="Render the scorecard page")
    bld.add_argument("--data", default=DATA)
    bld.add_argument("--out", default=OUT)
    bld.set_defaults(func=build)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
