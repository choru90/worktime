#!/usr/bin/env python3
"""Fail evidence verification when JUnit suites are absent, failed, or skipped."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    suites = sorted(Path('build/test-results/test').glob('TEST-*.xml'))
    if not suites:
        raise SystemExit('No JUnit XML suites found')
    totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    for path in suites:
        root = ET.parse(path).getroot()
        count = int(root.get('tests', 0))
        if count != len(root.findall('testcase')):
            raise SystemExit(f'JUnit testcase count mismatch: {path}')
        for key in totals:
            totals[key] += int(root.get(key, 0))
    message = 'JUnit measured results: ' + ', '.join(f'{k}={v}' for k, v in totals.items())
    print(message)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as output:
            output.write('\n' + message + '\n')
    if totals['tests'] == 0 or any(totals[key] for key in ('failures', 'errors', 'skipped')):
        raise SystemExit('JUnit evidence is not a complete passing execution')


if __name__ == '__main__':
    main()
