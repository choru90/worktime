import fcntl
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import reconcile_manifest as recovery


class ManifestRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.lock = self.root / 'reviewed-test.lock'
        self.lock.touch(mode=0o600)
        self.manifest = self.root / 'manifest.json'
        self.original = ('{\n  "migration_sha256": "' + recovery.OLD + '",\n'
                         '  "backend": "unchanged", "web": {"sha": "unchanged"}\n}\n').encode()
        self.manifest.write_bytes(self.original)
        self.manifest.chmod(0o600)
        (self.root / 'backups').mkdir(mode=0o700)
        (self.root / 'deploy.py').write_text('# synthetic fixture\n')
        self.addCleanup(patch.stopall)
        patch.object(recovery, 'CONTROLLER', recovery.digest(self.root / 'deploy.py')).start()

    def run_recovery(self, apply=False, verify=lambda root: None, acknowledgement=recovery.ACK):
        return recovery.reconcile(self.root, self.lock, apply, acknowledgement, verify)

    def test_dry_run_changes_no_file(self):
        before = {p.name: p.read_bytes() for p in self.root.iterdir() if p.is_file()}
        result = self.run_recovery()
        self.assertFalse(result['manifest_modified'])
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.root.iterdir() if p.is_file()})
        self.assertEqual([], list((self.root / 'backups').iterdir()))

    def test_apply_preserves_other_fields_bytes_owner_mode_and_verified_backup(self):
        original_info = self.manifest.stat()
        result = self.run_recovery(apply=True)
        self.assertTrue(result['manifest_modified'])
        self.assertTrue(result['controller_unchanged'])
        self.assertFalse(result['database_modified'])
        self.assertEqual(self.original.replace(recovery.OLD.encode(), recovery.NEW.encode()), self.manifest.read_bytes())
        after = self.manifest.stat()
        self.assertEqual((original_info.st_uid, original_info.st_gid, original_info.st_mode),
                         (after.st_uid, after.st_gid, after.st_mode))
        backup, = (self.root / 'backups').glob('*.json')
        self.assertEqual(self.original, backup.read_bytes())
        self.assertEqual(0o600, backup.stat().st_mode & 0o777)
        self.assertEqual(recovery.digest(backup), Path(str(backup) + '.sha256').read_text().strip())

    def test_no_approval_no_write(self):
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True, acknowledgement='')
        self.assertEqual(self.original, self.manifest.read_bytes())
        self.assertEqual([], list((self.root / 'backups').iterdir()))

    def test_live_precondition_failure_never_writes(self):
        def reject(root):
            raise recovery.Refused('database mismatch')
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True, verify=reject)
        self.assertEqual(self.original, self.manifest.read_bytes())

    def test_busy_lock_refuses(self):
        with self.lock.open('rb') as other:
            fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(recovery.Refused):
                self.run_recovery(apply=True)
        self.assertEqual(self.original, self.manifest.read_bytes())

    def test_second_attempt_refuses(self):
        self.run_recovery(apply=True)
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True)

    def test_ambiguous_or_changed_manifest_refuses(self):
        cases = [b'{}', json.dumps({'migration_sha256': recovery.NEW}).encode(),
                 json.dumps({'migration_sha256': recovery.OLD, 'nested': {'migration_sha256': recovery.OLD}}).encode(),
                 ('{"migration_sha256":"' + recovery.OLD + '","migration_sha256":"bad"}').encode(),
                 json.dumps({'migration_sha256': recovery.OLD, 'unrelated': recovery.OLD}).encode()]
        for raw in cases:
            with self.subTest(raw=raw), self.assertRaises((recovery.Refused, ValueError)):
                recovery.proposed_manifest(raw)

    def test_symlink_refuses(self):
        self.manifest.rename(self.root / 'real.json')
        self.manifest.symlink_to(self.root / 'real.json')
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True)
        self.assertEqual(self.original, (self.root / 'real.json').read_bytes())

    def test_backup_failure_never_replaces_manifest(self):
        with patch.object(recovery, 'write_private', side_effect=OSError('disk full')):
            with self.assertRaises(OSError):
                self.run_recovery(apply=True)
        self.assertEqual(self.original, self.manifest.read_bytes())

    def test_state_change_after_backup_never_replaces_manifest(self):
        calls = []
        def verify(root):
            calls.append(True)
            if len(calls) == 2:
                raise recovery.Refused('runtime changed')
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True, verify=verify)
        self.assertEqual(self.original, self.manifest.read_bytes())

    def test_replacement_failure_preserves_original(self):
        with patch.object(recovery.os, 'replace', side_effect=OSError('replace failed')):
            with self.assertRaises(OSError):
                self.run_recovery(apply=True)
        self.assertEqual(self.original, self.manifest.read_bytes())
        self.assertEqual([], list(self.root.glob('.wt002-manifest-*')))

    def test_manifest_change_during_preflight_is_not_overwritten(self):
        changed = self.original + b' '
        def verify(root):
            self.manifest.write_bytes(changed)
        with self.assertRaises(recovery.Refused):
            self.run_recovery(apply=True, verify=verify)
        self.assertEqual(changed, self.manifest.read_bytes())

class RuntimePreconditionsTest(unittest.TestCase):
    def runtime(self, **changes):
        root = Path('/opt/worktime')
        meta = {'sha': recovery.TARGET, 'service': 'backend', 'migration_sha256': recovery.NEW,
                'archive_sha256': recovery.ARCHIVE}
        meta.update(changes.get('metadata', {}))
        labels = {'com.docker.compose.project.working_dir': str(root)}
        apps = [{'Config': {'Image': 'worktime-backend:' + changes.get('image', recovery.CURRENT), 'Labels': labels},
                 'State': {'Running': True}},
                {'Id': 'synthetic-db', 'Config': {'Image': 'mysql:8.0', 'Labels': dict(labels, **{'com.docker.compose.service': 'mysql'})}}]
        outputs = iter(['synthetic-app synthetic-db', json.dumps(apps),
                        changes.get('sql', '1\t1\n2\t1\n3\t1\n4\t1\n5\t1\n2\n1\n')])
        def command(root, args, data=None):
            if data is not None:
                self.assertEqual(recovery.SQL, data)
                self.assertNotIn('UPDATE', data.upper())
            return next(outputs)
        def digest(path):
            return changes.get('controller', recovery.CONTROLLER) if path.name == 'deploy.py' else changes.get('archive', recovery.ARCHIVE)
        with patch.object(recovery, 'command', side_effect=command), patch.object(recovery, 'digest', side_effect=digest), patch.object(Path, 'read_bytes', return_value=json.dumps(meta).encode()):
            recovery.verify_runtime(root)

    def test_exact_diagnosed_runtime_passes_readonly_queries(self):
        self.runtime()

    def test_each_changed_runtime_condition_refuses(self):
        for change in [{'metadata': {'sha': 'different'}}, {'metadata': {'migration_sha256': recovery.OLD}},
                       {'metadata': {'archive_sha256': 'different'}}, {'archive': 'different'},
                       {'controller': 'different'}, {'image': recovery.TARGET},
                       {'sql': '1\t1\n2\t1\n3\t1\n4\t1\n0\n0\n'},
                       {'sql': '1\t1\n2\t1\n3\t1\n4\t1\n5\t0\n2\n1\n'}]:
            with self.subTest(change=change), self.assertRaises(recovery.Refused):
                self.runtime(**change)


if __name__ == '__main__':
    unittest.main()
