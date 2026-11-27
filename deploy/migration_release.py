"""Trusted controller extension. Never run from an uploaded release directory.

Installed only through separately reviewed bootstrap. Called under deploy.lock;
normal deploy's exact-main/archive checks and fingerprint comparison stay intact.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time

JOURNAL = 'migration-release.json'
RESUMING = False  # Set only by the explicit trusted resume entry point.


class Refused(RuntimeError):
    pass


def require(value, reason):
    if not value:
        raise Refused(reason)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''):
            h.update(chunk)
    return h.hexdigest()


def object_digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(',', ':')).encode()).hexdigest()


def unique(pairs):
    out = {}
    for k, v in pairs:
        require(k not in out, 'duplicate JSON key')
        out[k] = v
    return out


def load(path):
    require(not path.is_symlink() and path.is_file(), 'missing or unsafe state file')
    return json.loads(path.read_bytes(), object_pairs_hook=unique)


def sync(root):
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def atomic(path, value):
    require(not path.is_symlink(), 'state symlink refused')
    fd, temporary = tempfile.mkstemp(prefix='.migration-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as out:
            os.fchmod(out.fileno(), 0o600)
            json.dump(value, out, ensure_ascii=False, sort_keys=True)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
        sync(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def private_file(path, owner=None):
    if owner is None:
        owner = os.geteuid()
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid == owner
            and not info.st_mode & 0o077, 'trusted private regular file required')


def trusted_file(path):
    """Admin-owned code/approval, readable by the existing nonroot executor.

    No sudo/new execution identity. Credentials/state use private_file instead.
    """
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid == 0
            and stat.S_IMODE(info.st_mode) in {0o600, 0o640, 0o644},
            'administrator-owned read-only trusted file required')


def history(snapshot, version):
    applied, resolved = snapshot['history'], snapshot['resolved']
    require([r['version'] for r in resolved] == list(map(str, range(1, 8))), 'resolved migration set changed')
    require(len(applied) == version, 'unexpected migration history length')
    for row, expected in zip(applied, resolved):
        require(row['version'] == expected['version'] and row['checksum'] == expected['checksum']
                and row['success'] is True, 'migration history/checksum mismatch')


def preserved(before, after):
    for table, rows in before['tables'].items():
        current = after['tables'].get(table)
        require(isinstance(current, list) and len(rows) == len(current), 'business row count changed')
        for old, new in zip(rows, current):
            require(old == {k: new.get(k) for k in old} and old.keys() <= new.keys(), 'business value changed')
    require(all(c in after['columns'] for c in before['columns']), 'original column changed')
    require(all(c in after.get('indexes', []) for c in before.get('indexes', [])), 'original index changed')


def verified_backup(root, proof, snapshot):
    require(set(proof) == {'file', 'sha256', 'snapshot_sha256'}, 'invalid restore proof')
    name = proof['file']
    require(isinstance(name, str) and Path(name).name == name, 'backup path escapes state directory')
    path = root / name
    require(not path.is_symlink() and path.is_file() and digest(path) == proof['sha256'], 'backup changed')
    require(proof['snapshot_sha256'] == object_digest(snapshot), 'restored snapshot differs')


def guard_state(root, service, sha, resume=False):
    path = root / JOURNAL
    if path.exists() or path.is_symlink():
        state = load(path)
        require(resume and service == 'backend' and state['plan']['target_sha'] == sha,
                'migration pending; exact-target explicit resume or recovery review required')


def execute(root, plan, runtime, resume=False, checkpoint=lambda phase: None):
    """Caller owns the existing controller lock for this entire transaction/deploy."""
    root = Path(root)
    journal = root / JOURNAL
    manifest = root / 'manifest.json'
    exists = journal.exists() or journal.is_symlink()
    current = runtime.observe()
    require(current['identity'] == plan['identity'], 'database identity mismatch')
    if exists:
        state = load(journal)
        require(resume and state['plan'] == plan, 'explicit same-plan resume required')
        require(state['phase'] in {'verified6', 'verified7', 'approved'},
                'unverified checkpoint; manual DB/restore recovery review required')
        runtime.assert_frozen()
        version = 6 if state['phase'] == 'verified6' else 7
        history(current, version)
        require(current == state['snapshot'], 'database changed since checkpoint')
        verified_backup(root, state['v5_backup'], state['v5'])
        original = state['manifest']
        approved = {**original, 'migration_sha256': plan['migration_sha256']}
        require(load(manifest) == (approved if state['phase'] == 'approved' else original), 'manifest changed')
        if state['phase'] == 'approved':
            verified_backup(root, state['v7_backup'], state['snapshot'])
            return approved
    else:
        require(not resume, 'no pending migration to resume')
        original = load(manifest)
        require(original.get('backend') == plan['old_backend']
                and original.get('migration_sha256') == plan['old_migration_sha256'], 'manifest baseline changed')
        history(current, 5)
        state = {'plan': plan, 'manifest': original, 'phase': 'freezing'}
        atomic(journal, state)
        runtime.freeze()
        runtime.assert_frozen()
        current = runtime.observe()
        require(current['identity'] == plan['identity'], 'database identity changed')
        history(current, 5)
        state['v5'] = current
        state['v5_backup'] = runtime.backup_restore(root, 'v5', current)
        verified_backup(root, state['v5_backup'], current)
        require(runtime.observe() == current, 'database changed during backup/restore')
        state.update(phase='verified5', snapshot=current)
        atomic(journal, state)
    for version in (6, 7):
        if int(current['history'][-1]['version']) >= version:
            continue
        runtime.assert_frozen()
        require(runtime.observe() == current, 'database changed before migration')
        state['phase'] = 'migrating' + str(version)
        atomic(journal, state)  # Ambiguous MySQL DDL can never auto-resume.
        runtime.migrate(version)
        current = runtime.observe()
        require(current['identity'] == plan['identity'], 'database identity changed')
        history(current, version)
        preserved(state['v5'], current)
        require(current['tables'].get('push_devices') == []
                and current['tables'].get('push_deliveries') == [], 'new push tables must be empty')
        if version == 7:
            require(all(r.get('memo') is None and 'memo' in r for r in current['tables']['schedules']),
                    'existing schedule memo must be NULL')
        state.update(phase='verified' + str(version), snapshot=current)
        atomic(journal, state)
        checkpoint(state['phase'])
    runtime.assert_frozen()
    state['v7_backup'] = runtime.backup_restore(root, 'v7', current)
    verified_backup(root, state['v7_backup'], current)
    require(runtime.observe() == current, 'database changed before approval')
    require(load(manifest) == original, 'manifest changed before approval')
    approved = {**original, 'migration_sha256': plan['migration_sha256']}
    # A crash between these writes is intentionally not auto-resumable.
    atomic(manifest, approved)
    state['phase'] = 'approved'
    atomic(journal, state)
    return approved


def complete(root, service, sha):
    journal = root / JOURNAL
    if not journal.exists():
        return
    state = load(journal)
    require(service == 'backend' and state['phase'] == 'approved'
            and state['plan']['target_sha'] == sha, 'migration activation mismatch')
    active = load(root / 'manifest.json')
    require(active['backend'] == sha and active['migration_sha256'] == state['plan']['migration_sha256'],
            'migration target not activated')
    state['phase'] = 'activated'
    atomic(root / ('migration-completed-' + str(time.time_ns()) + '.json'), state)
    journal.unlink()
    sync(root)


def guard(root, service, sha):
    guard_state(root, service, sha, RESUMING)


def prepare(root, service, sha, incoming, old):
    if service != 'backend':
        return old
    meta = load(incoming / 'metadata.json')
    if meta['migration_sha256'] == old['migration_sha256'] and not (root / JOURNAL).exists():
        return old
    profile = root / 'migration-plan.json'
    trusted_file(profile)
    plan = load(profile)
    require(service == 'backend' and sha == plan['target_sha'], 'release not approved for migration')
    for key in ('migration_sha256', 'archive_sha256'):
        require(meta[key] == plan[key], 'approved artifact changed')
    require(digest(incoming / 'image.tar.gz') == plan['archive_sha256'], 'approved archive changed')
    require(meta.get('image_id') == plan['docker']['image_id'], 'approved image metadata changed')
    for name in ['deploy.py', 'migration_runtime.py', 'migration_release.py']:
        trusted_file(root / name)
    require(plan['controller_sha256'] == digest(root / 'deploy.py'), 'trusted controller changed')
    require(plan['runtime_sha256'] == digest(root / 'migration_runtime.py')
            and plan['release_sha256'] == digest(root / 'migration_release.py'), 'trusted extension changed')
    require(load(root / 'manifest.json') == old, 'controller manifest changed')
    from migration_runtime import DockerRuntime
    return execute(root, plan, DockerRuntime(root, plan, incoming), RESUMING)
