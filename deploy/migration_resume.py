"""Explicit exact-target resume, after separately authorized trusted bootstrap."""
import argparse
import fcntl
import importlib.util
from pathlib import Path
import sys

import migration_release as release


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('/opt/worktime'))
    parser.add_argument('--target-sha', required=True)
    parser.add_argument('--acknowledgement', required=True)
    args = parser.parse_args()
    root = args.root
    release.require(root.is_absolute() and root.resolve() == root, 'canonical trusted root required')
    release.require(args.acknowledgement == 'reviewed-frozen-exact-target-resume', 'explicit resume acknowledgement required')
    for name in ['deploy.py', 'migration_release.py', 'migration_runtime.py', 'migration-plan.json']:
        release.trusted_file(root / name)
    plan = release.load(root / 'migration-plan.json')
    release.require(plan['target_sha'] == args.target_sha, 'resume target changed')
    for key, name in [('controller_sha256','deploy.py'), ('runtime_sha256','migration_runtime.py'), ('release_sha256','migration_release.py')]:
        release.require(plan[key] == release.digest(root / name), 'trusted code changed')
    # The patched controller itself acquires the existing deploy.lock; do not
    # acquire a second descriptor around deploy() and deadlock the same flock.
    release.RESUMING = True
    spec = importlib.util.spec_from_file_location('reviewed_deploy', root / 'deploy.py')
    controller = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(controller)
    result = controller.deploy(root, 'backend', args.target_sha, root / 'incoming' / 'backend' / args.target_sha)
    release.require(not (root / release.JOURNAL).exists(), 'resume did not complete migration activation')
    print(result)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('Resume refused/failed; keep writers frozen and review private journal', file=sys.stderr)
        raise SystemExit(1)
