#!/usr/bin/env python3
"""Turn Failsafe XML reports from contract-tests into a Markdown summary.

Usage:
    python3 scripts/contract_report.py --target "JBoss EAP 8.1 (legacy kitchensink)" \
        --out docs/contract/legacy-baseline.md
"""
import argparse
import datetime
import glob
import os
import re
import subprocess
import xml.etree.ElementTree as ET

PACKAGE = "io.github.mighil31.kitchensink.contract."


def git_head():
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def readable(name):
    """'invalidName_isRejected(String, Set) name "R2D2" -> 400' -> 'invalidName_isRejected: name "R2D2" -> 400'."""
    match = re.match(r"^(\w+)\([^)]*\) (.+)$", name)
    return f"{match[1]}: {match[2]}" if match else name


def status_of(case):
    for tag, label in (("failure", "❌ failed"), ("error", "❌ error"), ("skipped", "⏭ skipped")):
        if case.find(tag) is not None:
            return label
    return "✅"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reports", default="contract-tests/target/failsafe-reports")
    parser.add_argument("--target", required=True, help="Human-readable name of the app under test")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    files = sorted(glob.glob(os.path.join(args.reports, "TEST-*.xml")))
    if not files:
        raise SystemExit(f"No Failsafe reports in {args.reports}; run mvn verify first")

    suites, props = [], {}
    for path in files:
        root = ET.parse(path).getroot()
        props.update({p.get("name"): p.get("value") for p in root.iter("property")})
        cases = sorted((readable(c.get("name")), status_of(c)) for c in root.iter("testcase"))
        suites.append((root.get("name").removeprefix(PACKAGE), cases))

    total = sum(len(c) for _, c in suites)
    passed = sum(1 for _, cases in suites for _, s in cases if s == "✅")

    lines = [
        f"# Contract test report: {args.target}",
        "",
        "| | |",
        "|---|---|",
        f"| Date | {datetime.date.today().isoformat()} |",
        f"| Target | {args.target} |",
        f"| Base URL | `{props.get('contract.baseUrl', '?')}` |",
        f"| Format | `{props.get('contract.format', '?')}` |",
        f"| Suite commit | `{git_head()}` |",
        f"| Java | {props.get('java.version', '?')} |",
        f"| Result | **{passed}/{total} passed** |",
        "",
    ]
    for suite, cases in suites:
        lines += [f"## {suite}", "", "| Test | Result |", "|---|---|"]
        lines += [f"| {name.replace('|', '\\|')} | {status} |" for name, status in cases]
        lines.append("")

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Wrote {args.out}: {passed}/{total} passed")


if __name__ == "__main__":
    main()
