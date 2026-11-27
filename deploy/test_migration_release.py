import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import migration_release as release


class Runtime:
    def __init__(self):
        self.version = 5
        self.frozen = False
        self.fail = None
        self.calls = []
        self.changed = False

    def observe(self):
        return {'identity': {'server_uuid': 'synthetic', 'database': 'source'},
                'history': [{'version': str(n), 'checksum': n, 'success': True} for n in range(1, self.version + 1)],
                'resolved': [{'version': str(n), 'checksum': n} for n in range(1, 8)],
                'tables': {'schedules': [{'id': '1', 'value': 'changed' if self.changed else 'original', **({'memo': None} if self.version == 7 else {})}],
                           **({'push_devices': [], 'push_deliveries': []} if self.version >= 6 else {})},
                'columns': ['v5'] + (['v6'] if self.version >= 6 else []) + (['v7'] if self.version >= 7 else [])}

    def freeze(self):
        self.frozen = True
        self.calls.append('freeze')

    def assert_frozen(self):
        release.require(self.frozen, 'writers not frozen')

    def backup_restore(self, root, label, snapshot):
        self.calls.append('restore' + label)
        if self.fail == 'backup':
            raise release.Refused('backup failed')
        path = root / (label + '.sql')
        path.write_bytes(b'synthetic verified dump')
        return {'file': path.name, 'sha256': release.digest(path), 'snapshot_sha256': release.object_digest(snapshot)}

    def migrate(self, version):
        self.calls.append('migrate' + str(version))
        if self.fail == 'migrate':
            raise release.Refused('unknown DDL outcome')
        self.version = version


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.runtime = Runtime()
        self.old = {'backend': 'a' * 40, 'web': 'b' * 40, 'migration_sha256': 'c' * 64, 'untouched': 'yes'}
        self.plan = {'target_sha': 'd' * 40, 'old_backend': 'a' * 40, 'old_migration_sha256': 'c' * 64,
                     'migration_sha256': 'e' * 64, 'archive_sha256': 'f' * 64,
                     'identity': {'server_uuid': 'synthetic', 'database': 'source'}}
        (self.root / 'manifest.json').write_text(json.dumps(self.old))

    def tearDown(self):
        self.temp.cleanup()

    def execute(self, resume=False):
        return release.execute(self.root, self.plan, self.runtime, resume=resume)

    def test_restore_proof_precedes_migration_and_only_approval_changes(self):
        result = self.execute()
        self.assertEqual(self.runtime.calls, ['freeze', 'restorev5', 'migrate6', 'migrate7', 'restorev7'])
        self.assertEqual(result, {**self.old, 'migration_sha256': 'e' * 64})
        self.assertEqual(release.load(self.root / release.JOURNAL)['phase'], 'approved')
        with self.assertRaises(release.Refused):
            release.guard_state(self.root, 'web', 'b' * 40, resume=False)

    def test_identity_mismatch_precedes_freeze(self):
        self.plan['identity']['server_uuid'] = 'another'
        with self.assertRaises(release.Refused): self.execute()
        self.assertEqual(self.runtime.calls, [])
        self.assertEqual(release.load(self.root / 'manifest.json'), self.old)

    def test_history_checksum_and_extra_migration_refused(self):
        original = self.runtime.observe
        for change in ['checksum', 'version']:
            def bad():
                s = original()
                s['history'][0][change] = 'unexpected'
                return s
            self.runtime.observe = bad
            with self.assertRaises(release.Refused): self.execute()
        self.assertEqual(self.runtime.calls, [])

    def test_backup_failure_leaves_manifest_and_blocks_resume(self):
        self.runtime.fail = 'backup'
        with self.assertRaises(release.Refused): self.execute()
        self.assertTrue(self.runtime.frozen)
        self.assertEqual(release.load(self.root / 'manifest.json'), self.old)
        with self.assertRaises(release.Refused): self.execute(resume=True)
        self.assertFalse(any(x.startswith('migrate') for x in self.runtime.calls))

    def test_partial_ddl_requires_manual_recovery(self):
        self.runtime.fail = 'migrate'
        with self.assertRaises(release.Refused): self.execute()
        self.runtime.fail = None
        with self.assertRaises(release.Refused): self.execute(resume=True)
        self.assertEqual(release.load(self.root / 'manifest.json'), self.old)

    def test_verified_checkpoint_requires_explicit_same_plan_resume(self):
        def interrupt(phase):
            if phase == 'verified6': raise RuntimeError('simulated process loss')
        with self.assertRaises(RuntimeError):
            release.execute(self.root, self.plan, self.runtime, checkpoint=interrupt)
        with self.assertRaises(release.Refused): self.execute()
        changed = copy.deepcopy(self.plan)
        changed['target_sha'] = '9' * 40
        with self.assertRaises(release.Refused): release.execute(self.root, changed, self.runtime, resume=True)
        result = self.execute(resume=True)
        self.assertEqual(result['migration_sha256'], 'e' * 64)
        self.assertEqual(self.runtime.calls.count('migrate6'), 1)

    def test_changed_data_or_backup_refuses_resume(self):
        def interrupt(phase):
            if phase == 'verified6': raise RuntimeError('stop')
        with self.assertRaises(RuntimeError): release.execute(self.root, self.plan, self.runtime, checkpoint=interrupt)
        self.runtime.changed = True
        with self.assertRaises(release.Refused): self.execute(resume=True)
        self.runtime.changed = False
        (self.root / 'v5.sql').write_bytes(b'corrupt')
        with self.assertRaises(release.Refused): self.execute(resume=True)

    def test_approved_retry_requires_ack_and_manifest_still_matches(self):
        result = self.execute()
        with self.assertRaises(release.Refused): self.execute()
        self.assertEqual(self.execute(resume=True), result)
        release.atomic(self.root / 'manifest.json', {**result, 'web': '9' * 40})
        with self.assertRaises(release.Refused): self.execute(resume=True)

    def test_success_receipt_only_after_exact_activated_manifest(self):
        self.execute()
        with self.assertRaises(release.Refused): release.complete(self.root, 'backend', 'd' * 40)
        release.atomic(self.root / 'manifest.json', {**self.old, 'backend': 'd' * 40, 'migration_sha256': 'e' * 64})
        release.complete(self.root, 'backend', 'd' * 40)
        self.assertFalse((self.root / release.JOURNAL).exists())
        self.assertTrue(list(self.root.glob('migration-completed-*.json')))

    def test_crash_after_manifest_approval_before_journal_update_refuses(self):
        atomic = release.atomic
        def interrupt(path, state):
            if path.name == release.JOURNAL and state.get('phase') == 'approved':
                raise OSError('simulated power loss after manifest replacement')
            return atomic(path, state)
        with patch.object(release, 'atomic', side_effect=interrupt):
            with self.assertRaises(OSError): self.execute()
        self.assertEqual(release.load(self.root / 'manifest.json')['migration_sha256'], 'e'*64)
        self.assertEqual(release.load(self.root / release.JOURNAL)['phase'], 'verified7')
        with self.assertRaises(release.Refused): self.execute(resume=True)
        with self.assertRaises(release.Refused): release.guard_state(self.root,'web','b'*40)

    def test_backend_rollback_restarted_writer_blocks_approved_resume(self):
        self.execute()
        self.runtime.frozen = False  # normal controller rollback brought the old backend back up
        with self.assertRaises(release.Refused): self.execute(resume=True)
        self.assertEqual(release.load(self.root / release.JOURNAL)['phase'], 'approved')

    def test_approved_resume_requires_both_proofs_and_unchanged_database(self):
        self.execute()
        for name in ['v5.sql','v7.sql']:
            path = self.root / name
            original = path.read_bytes()
            path.write_bytes(b'corrupted restore evidence')
            with self.assertRaises(release.Refused): self.execute(resume=True)
            path.write_bytes(original)
        self.runtime.changed = True
        with self.assertRaises(release.Refused): self.execute(resume=True)
        self.assertEqual(release.load(self.root / release.JOURNAL)['phase'], 'approved')


if __name__ == '__main__': unittest.main()
