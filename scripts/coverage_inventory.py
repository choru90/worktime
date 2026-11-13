#!/usr/bin/env python3
"""Export raw JaCoCo gaps without changing measurement scope or excluding code."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def counters(node):
    return {c.get('type'): {'covered': int(c.get('covered')), 'missed': int(c.get('missed'))}
            for c in node.findall('counter')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('xml', nargs='?', default='build/reports/jacoco/test/jacocoTestReport.xml')
    parser.add_argument('--output', default='build/reports/jacoco/coverage-inventory.json')
    args = parser.parse_args()
    root = ET.parse(args.xml).getroot()
    packages = []
    for package in root.findall('package'):
        classes = []
        for clazz in package.findall('class'):
            methods = [{**method.attrib, 'counters': counters(method)} for method in clazz.findall('method')]
            classes.append({**clazz.attrib, 'counters': counters(clazz), 'methods': methods})
        sources = []
        for source in package.findall('sourcefile'):
            gaps = [{key: int(value) for key, value in line.attrib.items()}
                    for line in source.findall('line')
                    if int(line.get('mi', 0)) or int(line.get('mb', 0))]
            sources.append({'name': source.get('name'), 'gaps': gaps})
        packages.append({'name': package.get('name'), 'counters': counters(package),
                         'classes': classes, 'sources': sources})
    report = {'totals': counters(root), 'packages': packages}
    for kind in ('LINE', 'BRANCH'):
        count = report['totals'].get(kind)
        if count is None or count['covered'] + count['missed'] == 0:
            raise SystemExit(f'Missing {kind} counters: refusing to certify coverage')
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Raw coverage inventory: {output} ({len(packages)} packages)')


if __name__ == '__main__':
    main()
