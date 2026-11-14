#!/usr/bin/env python3
"""Audit JaCoCo XML and report every missed class and uncovered branch.

This is a deterministic reviewer, not an autonomous code-writing agent.
No coverage exclusions are applied.
"""
import argparse
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def counters(element):
    return {
        c.get("type"): (int(c.get("covered", "0")), int(c.get("missed", "0")))
        for c in element.findall("counter")
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", nargs="?", default="build/reports/jacoco/test/jacocoTestReport.xml")
    parser.add_argument("--require-100", action="store_true", help="Exit nonzero unless both LINE and BRANCH reach 100%")
    args = parser.parse_args()
    source = Path(args.xml)
    if not source.is_file():
        sys.exit(f"JaCoCo XML missing: {source}")
    root = ET.parse(source).getroot()
    totals = counters(root)
    output = ["## Backend coverage audit (no exclusions)", ""]
    incomplete = False
    for kind in ("LINE", "BRANCH"):
        covered, missed = totals.get(kind, (0, 0))
        total = covered + missed
        if total == 0:
            sys.exit(f"Missing {kind} coverage data; cannot certify 100%")
        pct = 100 * covered / total
        output.append(f"- **{kind}**: {pct:.2f}% ({covered}/{total}), missed **{missed}**")
        incomplete |= missed > 0
    output += ["", "### Uncovered classes (sorted by missed branches, then lines)", "",
               "| Class | Missed branches | Missed lines |",
               "|---|---:|---:|"]
    gaps = []
    for package in root.findall("package"):
        for clazz in package.findall("class"):
            counts = counters(clazz)
            missed_lines = counts.get("LINE", (0, 0))[1]
            missed_branches = counts.get("BRANCH", (0, 0))[1]
            if missed_lines or missed_branches:
                gaps.append((missed_branches, missed_lines, clazz.get("name", "?")))
    for branches, lines, name in sorted(gaps, key=lambda row: (-row[0], -row[1], row[2])):
        output.append(f"| `{name}` | {branches} | {lines} |")
    output += ["", f"**Review verdict:** {'NOT 100% — continue tests and remeasure' if incomplete else '100% LINE and BRANCH'}",
               f"**Remaining classes:** {len(gaps)}", ""]
    report = "\n".join(output)
    print(report)
    Path("build/reports/jacoco/coverage-audit.md").write_text(report, encoding="utf-8")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(report)
    if args.require_100 and incomplete:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
