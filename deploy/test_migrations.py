import unittest,tempfile
from pathlib import Path
from migrations import fingerprint,files
class MigrationFingerprintTest(unittest.TestCase):
    def test_kotlin_change_invalidates_rollback_compatibility(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);folder=root/'src/main/kotlin/db/migration';folder.mkdir(parents=True)
            p=folder/'V4__upgrade.kt';p.write_text('original migration')
            original=fingerprint(root);p.write_text('changed migration')
            self.assertNotEqual(original,fingerprint(root))


    def test_repository_sql_and_jvm_versions_are_unique(self):
        import re
        root=Path(__file__).resolve().parents[1]
        versions=[re.match(r'^V([^_]+)__',p.name).group(1) for p in files(root) if re.match(r'^V([^_]+)__',p.name)]
        self.assertEqual(len(versions),len(set(versions)))
