import ast
import hashlib
import unittest
import tempfile
from pathlib import Path
import fcntl
from unittest.mock import patch
from bootstrap_migration import candidate
from migration_release import Refused

SOURCE = '''import fcntl
def deploy(root, service, sha, incoming):
    with (root / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        journal = root / 'journal.json'
        if journal.exists():
            raise RuntimeError('interrupted deployment')
        old = {'migration_sha256': 'old'}
        meta = {'migration_sha256': 'new'}
        if service == 'backend' and meta['migration_sha256'] != old['migration_sha256']:
            raise RuntimeError('migration change requires reviewed release')
        try:
            new = dict(old)
            smoke(root)
            atomic_json(root / 'manifest.json', new)
        except Exception:
            raise
        journal.unlink()
'''


class BootstrapTest(unittest.TestCase):
    def test_all_hooks_hold_existing_exclusive_lock(self):
        source = candidate(SOURCE, hashlib.sha256(SOURCE.encode()).hexdigest())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'journal.json').write_text('synthetic journal')
            # The normal fixture's interruption check needs no prior journal.
            (root / 'journal.json').unlink()
            calls = []
            def locked(name):
                with (root / 'deploy.lock').open('r') as contender:
                    with self.assertRaises(BlockingIOError):
                        fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
                calls.append(name)
            def prepare(*args):
                locked('prepare')
                return {'migration_sha256':'new'}
            namespace = {'smoke':lambda root:None,'atomic_json':lambda path,data:None}
            exec(source, namespace)
            # fixture unlinks the original controller journal after activation
            namespace['atomic_json'] = lambda path,data:(root/'journal.json').touch()
            with patch('migration_release.guard',side_effect=lambda *a:locked('guard')), \
                 patch('migration_release.prepare',side_effect=prepare), \
                 patch('migration_release.complete',side_effect=lambda *a:locked('complete')):
                namespace['deploy'](root,'backend','a'*40,root/'incoming')
            self.assertEqual(calls,['guard','prepare','complete'])

    def test_pinned_patch_preserves_guard_and_same_flock(self):
        patched = candidate(SOURCE, hashlib.sha256(SOURCE.encode()).hexdigest())
        tree = ast.parse(patched)
        guards = [n for n in ast.walk(tree) if isinstance(n, ast.If) and "meta['migration_sha256'] != old['migration_sha256']" in ast.unparse(n.test)]
        self.assertEqual(len(guards), 1)
        self.assertIn("raise RuntimeError('migration change requires reviewed release')", ast.unparse(guards[0]))
        self.assertEqual(patched.count('fcntl.flock(lock, fcntl.LOCK_EX)'), 1)
        self.assertLess(patched.index('migration_release.guard('), patched.index('journal ='))
        self.assertLess(patched.index('migration_release.prepare('), patched.index("if service == 'backend'"))
        self.assertGreater(patched.index('migration_release.complete('), patched.index("atomic_json(root / 'manifest.json'"))

    def test_wrong_hash_unknown_shape_and_repeated_patch_refuse(self):
        with self.assertRaises(Refused): candidate(SOURCE, '0' * 64)
        for source in [SOURCE.replace('LOCK_EX', 'LOCK_SH'), SOURCE.replace('meta[', 'other['), SOURCE + '\nimport migration_release\n']:
            with self.assertRaises(Refused): candidate(source, hashlib.sha256(source.encode()).hexdigest())


if __name__ == '__main__': unittest.main()
