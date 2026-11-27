"""Validate explicit prepare/reuse modes; no deployment or network side effects."""
import json
import os
from pathlib import Path
import re
import sys
from migration_release import require


def validate_mode(deploy, prepare, prepared_run, ref):
    require(not (deploy and prepare), 'prepare and deploy are separate approvals')
    require(not (deploy or prepare or prepared_run) or ref == 'refs/heads/main', 'release modes require main')
    if prepared_run:
        require(deploy and not prepare and re.fullmatch('[0-9]{1,20}', prepared_run), 'prepared run is valid only for explicit deployment')


def validate_prepared(run, sha):
    require(run.get('status') == 'completed' and run.get('conclusion') == 'success'
            and run.get('event') == 'workflow_dispatch' and run.get('head_branch') == 'main'
            and run.get('head_sha') == sha and run.get('path') == '.github/workflows/staging.yml',
            'prepared artifact provenance does not match this successful main workflow revision')


def main():
    if sys.argv[1:] == ['mode']:
        validate_mode(os.environ.get('DEPLOY_STAGING') == 'true', os.environ.get('PREPARE_ARTIFACT') == 'true',
                      os.environ.get('PREPARED_RUN_ID',''), os.environ['GITHUB_REF'])
    elif len(sys.argv) == 3 and sys.argv[1] == 'provenance':
        validate_prepared(json.loads(Path(sys.argv[2]).read_text()), os.environ['GITHUB_SHA'])
    else:
        raise SystemExit('Use mode or provenance <private-run-json>')


if __name__ == '__main__': main()
