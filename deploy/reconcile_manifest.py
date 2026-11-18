#!/usr/bin/env python3
"""Review-only by default. Reconcile one diagnosed V4 approval after DB V5 checks.

Never invoke deploy.py, rewrite its guard, deploy an image, or execute migrations.
The operator must first review the pinned controller's manifest semantics and lock.
"""
import argparse
import ast
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import uuid

OLD = 'bfb996c0c85bf871f029616e5d74c0a6fb7e14b1f59e8c176fff660929c48fda'
NEW = 'c276840a0438395c0f32e9844dcda29137c03a194df7212bb48462d34a14316d'
CONTROLLER = '5ffbf53db65ada79f002f1d825c403870cca047179ea0dd7a57b1b63c126a6ae'
CURRENT = '5fe566ceb5a65a0a8d9bfa081500f8013031fa3d'
TARGET = '7466b0e35d9546731e024dd7b02e267701a04f19'
ARCHIVE = 'f560ae792f273ec4efab4ab4e0c10986acedb9d95c4943d4b83f5da655ee9017'
ACK = 'WT-002-reconcile-only-reviewed-V5'
SQL = '''SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work_logs' AND COLUMN_NAME IN ('correction_count','correction_reason');
SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work_log_corrections';'''


class Refused(RuntimeError):
    pass


def require(condition, reason):
    if not condition:
        raise Refused(reason)


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''):
            result.update(chunk)
    return result.hexdigest()


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def parse(raw):
    return json.loads(raw, object_pairs_hook=unique_keys)


def proposed_manifest(raw):
    """Preserve original bytes except the single verified approval value."""
    document = parse(raw)
    require(isinstance(document, dict) and document.get("backend") == CURRENT
            and document.get("migration_sha256") == OLD, "manifest identity or root baseline changed")
    fields = []

    def visit(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key == 'migration_sha256':
                    fields.append((value, key, item))
                elif isinstance(item, (dict, list)):
                    visit(item)
        elif isinstance(value, list):
            for item in value:
                visit(item)

    visit(document)
    require(len(fields) == 1 and fields[0][2] == OLD, 'approval shape or baseline changed')
    require(raw.count(OLD.encode()) == 1, 'ambiguous baseline bytes')
    expected = copy.deepcopy(document)
    fields[0][0][fields[0][1]] = NEW
    updated = raw.replace(OLD.encode(), NEW.encode(), 1)
    require(parse(updated) == document, 'replacement changed unrelated fields')
    require(parse(raw) == expected, 'original manifest changed')
    return updated


def regular(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, 'expected regular unlinked file')
    require(not info.st_mode & 0o022, 'unsafe writable file permissions')
    return info


def signature(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_nlink, info.st_uid, info.st_gid,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def command(root, args, data=None):
    result = subprocess.run(args, input=data, text=True, capture_output=True, cwd=root, timeout=45)
    require(result.returncode == 0, 'server query failed; output suppressed')
    return result.stdout


def require_no_journal(root):
    # Resolve the journal path structurally from the exact reviewed controller.
    # Never execute/import server code or assume a journal filename.
    tree = ast.parse((root / 'deploy.py').read_text())
    deploy = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'deploy']
    require(len(deploy) == 1, 'controller deploy structure changed')
    assignments = [n.value for n in ast.walk(deploy[0]) if isinstance(n, ast.Assign)
                   and any(isinstance(t, ast.Name) and t.id == 'journal' for t in n.targets)]
    require(len(assignments) == 1, 'journal structure changed')
    value = assignments[0]
    require(isinstance(value, ast.BinOp) and isinstance(value.op, ast.Div)
            and isinstance(value.left, ast.Name) and value.left.id == 'root'
            and isinstance(value.right, ast.Constant) and isinstance(value.right.value, str),
            'journal path structure changed')
    name = value.right.value
    require(bool(name) and Path(name).name == name and name not in {'.', '..'}, 'journal path escapes root')
    journal = root / name
    require(not journal.exists() and not journal.is_symlink(), 'interrupted deployment requires review')


def verify_runtime(root):
    require(digest(root / 'deploy.py') == CONTROLLER, 'controller changed')
    require_no_journal(root)
    incoming = root / 'incoming' / 'backend' / TARGET
    metadata = parse((incoming / 'metadata.json').read_bytes())
    require(metadata.get('sha') == TARGET and metadata.get('service') == 'backend', 'incoming identity changed')
    require(metadata.get('migration_sha256') == NEW, 'incoming migrations changed')
    require(metadata.get('archive_sha256') == ARCHIVE and digest(incoming / 'image.tar.gz') == ARCHIVE,
            'incoming checksum changed')
    ids = command(root, ['docker', 'ps', '--format', '{{.ID}}']).split()
    require(bool(ids), 'no running containers')
    containers = json.loads(command(root, ['docker', 'inspect'] + ids))
    scoped = [c for c in containers if c.get('Config', {}).get('Labels', {}).get(
        'com.docker.compose.project.working_dir') == str(root)]
    apps = [c for c in scoped if c['Config'].get('Image', '').split(':')[0].endswith('worktime-backend')]
    require(len(apps) == 1 and apps[0]['Config']['Image'].endswith(':' + CURRENT)
            and apps[0].get('State', {}).get('Running') is True, 'running backend changed')
    databases = [c for c in scoped if c['Config'].get('Labels', {}).get('com.docker.compose.service') == 'mysql'
                 and 'mysql' in c['Config'].get('Image', '').lower()]
    require(len(databases) == 1, 'expected one scoped MySQL')
    auth = ('set -eu; if [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ]; then '
            'export MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; else '
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi; '
            'exec mysql -u root --batch --skip-column-names "$MYSQL_DATABASE"')
    rows = command(root, ['docker', 'exec', '-i', databases[0]['Id'], 'sh', '-c', auth], SQL).strip().splitlines()
    require(rows == ['1\t1', '2\t1', '3\t1', '4\t1', '5\t1', '2', '1'], 'database is not verified V5')


def sync_dir(folder):
    fd = os.open(folder, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def write_private(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def reconcile(root, lock_path, apply=False, acknowledgement='', verify=verify_runtime):
    require(root.is_absolute() and root == root.resolve(), 'root must be canonical absolute path')
    require(lock_path.is_absolute() and lock_path.parent == root, 'reviewed lock must be directly under root')
    # Never invent/create a lock: caller must verify this is the controller's actual lock.
    regular(lock_path)
    fd = os.open(lock_path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refused('deployment lock busy') from None
        manifest = root / 'manifest.json'
        info = regular(manifest)
        require(info.st_size <= 1048576, 'manifest too large')
        original = manifest.read_bytes()
        updated = proposed_manifest(original)
        verify(root)
        if not apply:
            return {'preflight_passed': True, 'manifest_modified': False, 'deployment_started': False}
        require(acknowledgement == ACK, 'explicit reconciliation acknowledgement required')
        backups = root / 'backups'
        require(backups.is_dir() and not backups.is_symlink(), 'existing backup directory required')
        require(not backups.stat().st_mode & 0o077, 'backup directory must be private')
        backup = backups / ('wt002-manifest-' + uuid.uuid4().hex + '.json')
        write_private(backup, original)
        checksum = hashlib.sha256(original).hexdigest()
        write_private(Path(str(backup) + '.sha256'), (checksum + '\n').encode())
        sync_dir(backups)
        require(digest(backup) == checksum, 'backup verification failed')
        verify(root)
        require(manifest.read_bytes() == original and signature(regular(manifest)) == signature(info), 'manifest changed during checks')
        staged = None
        try:
            temp_fd, staged = tempfile.mkstemp(prefix='.wt002-manifest-', dir=root)
            with os.fdopen(temp_fd, 'wb') as stream:
                os.fchown(stream.fileno(), info.st_uid, info.st_gid)
                os.fchmod(stream.fileno(), stat.S_IMODE(info.st_mode))
                stream.write(updated)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(staged, manifest)
            staged = None
            sync_dir(root)
            require(manifest.read_bytes() == updated, 'manifest post-write verification failed')
        finally:
            if staged is not None:
                os.unlink(staged)
        return {'preflight_passed': True, 'manifest_modified': True, 'backup_verified': True,
                'deployment_started': False, 'database_modified': False,
                'controller_unchanged': digest(root / 'deploy.py') == CONTROLLER}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('/opt/worktime'))
    parser.add_argument('--reviewed-lock-file', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--acknowledgement', default='')
    args = parser.parse_args()
    try:
        result = reconcile(args.root, args.reviewed_lock_file, args.apply, args.acknowledgement)
    except (Refused, OSError, ValueError, subprocess.SubprocessError) as error:
        # Refused contains static reason strings only; IO/query exception text stays private.
        reason = str(error) if isinstance(error, Refused) else 'server IO, parse or query failure'
        print(json.dumps({'reconciliation_completed': False, 'refusal_reason': reason,
                          'sensitive_output_suppressed': True}))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
