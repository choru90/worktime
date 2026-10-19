import unittest,tempfile
from pathlib import Path
from migrations import fingerprint
class MigrationFingerprintTest(unittest.TestCase):
    def test_kotlin_change_invalidates_rollback_compatibility(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);folder=root/'src/main/kotlin/db/migration';folder.mkdir(parents=True)
            p=folder/'V4__upgrade.kt';p.write_text('original migration')
            original=fingerprint(root);p.write_text('changed migration')
            self.assertNotEqual(original,fingerprint(root))
