"""Opt-in actual MySQL regression, fixed fresh loopback schemas only.

Run gradlew migrationReleaseTestClasspath first. Set WORKTIME_RELEASE_TEST_PORT,
WORKTIME_RELEASE_TEST_PASSWORD and optional MYSQL_BIN/JAVA. Never targets staging.
"""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

import migration_release as release

REPO = Path(__file__).resolve().parent.parent
ENABLED = bool(os.environ.get('WORKTIME_RELEASE_TEST_PORT'))


class LocalMysqlRuntime:
    def __init__(self, case, schema):
        self.case, self.schema = case, schema
        self.frozen = False
        self.fail_after6 = False
        self.corrupt_restore = False
        self.env = case.environment(schema)

    def cli(self, action, schema=None, env_override=None):
        env = self.env if schema is None else self.case.environment(schema)
        if env_override: env = {**env, **env_override}
        p = subprocess.run([self.case.java, '-Dloader.main=com.worktime.deploy.MigrationCli', '-cp', str(self.case.jar),
                            'org.springframework.boot.loader.launch.PropertiesLauncher', action],
                           capture_output=True, env=env, timeout=90)
        if p.returncode:
            raise release.Refused('actual migration CLI refused')
        lines = [x.split('=',1)[1] for x in p.stdout.decode().splitlines() if x.startswith('WORKTIME_MIGRATION_JSON=')]
        release.require(len(lines) == 1, 'missing actual CLI snapshot')
        return json.loads(lines[0])

    def observe(self): return self.cli('snapshot')
    def freeze(self): self.frozen = True
    def assert_frozen(self): release.require(self.frozen, 'synthetic writer not frozen')
    def migrate(self, version): self.cli('migrate' + str(version))

    def backup_restore(self, root, label, snapshot):
        target = self.schema + '_' + label
        self.case.empty_schema(target)
        raw = self.case.sql(self.schema, dump=True)
        release.require(raw and not re.search(rb'(?im)^\s*(?:CREATE|DROP)\s+DATABASE|^\s*USE\s', raw), 'dump switches schema')
        path = root / ('backup-' + label + '.sql')
        with path.open('xb') as out: out.write(raw)
        self.case.sql(target, data=raw)
        if self.corrupt_restore:
            self.case.sql(target, "UPDATE users SET name='corrupted'")
        restored = self.cli('snapshot', target)
        restored['identity'] = snapshot['identity']
        release.require(restored == snapshot, 'actual restored data differs')
        return {'file': path.name, 'sha256': release.digest(path), 'snapshot_sha256': release.object_digest(snapshot)}


@unittest.skipUnless(ENABLED, 'explicit disposable MySQL port not configured')
class MysqlReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        port = os.environ['WORKTIME_RELEASE_TEST_PORT']
        if not re.fullmatch('[0-9]{4,5}', port): raise RuntimeError('explicit test port required')
        cls.port = port
        cls.java = os.environ.get('JAVA', 'java')
        cls.bin = Path(os.environ.get('MYSQL_BIN', '/usr/bin'))
        cls.env = {**os.environ, 'MYSQL_PWD': os.environ['WORKTIME_RELEASE_TEST_PASSWORD']}
        cls.jar = REPO / 'build/libs/worktime-0.0.1-SNAPSHOT.jar'
        cls.cp = (REPO / 'build/migration-release-test-classpath.txt').read_text()
        cls.uuid = cls.sql(None, 'SELECT @@server_uuid').decode().strip()
        databases = cls.sql(None, 'SHOW DATABASES').decode().splitlines()
        allowed = {'information_schema','mysql','performance_schema','sys','worktime'}
        if not set(databases) <= allowed:
            raise RuntimeError('refuse MySQL containing any unrelated/existing test schema; fresh disposable server required')
        if 'worktime' in databases and cls.sql(None, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='worktime'").strip() != b'0':
            raise RuntimeError('refuse nonempty normal application schema')

    @classmethod
    def sql(cls, schema, query=None, dump=False, data=None):
        if schema is not None and not re.fullmatch('worktime_release_test_[a-z0-9_]+', schema):
            raise RuntimeError('only fixed disposable schema prefix permitted')
        cmd = [str(cls.bin / ('mysqldump' if dump else 'mysql')), '--no-defaults', '--protocol=TCP',
               '--host=127.0.0.1', '--port=' + cls.port, '--user=root', '--default-character-set=utf8mb4']
        if dump: cmd += ['--single-transaction','--quick','--hex-blob','--routines','--triggers','--events',
                         '--set-gtid-purged=OFF','--no-tablespaces','--column-statistics=0','--skip-comments','--skip-dump-date']
        else: cmd += ['--batch', '--skip-column-names']
        if schema: cmd += [schema]
        if query is not None: cmd += ['-e', query]
        p = subprocess.run(cmd, input=data, capture_output=True, env=cls.env, timeout=90)
        if p.returncode: raise RuntimeError('synthetic MySQL command failed; output suppressed')
        return p.stdout

    @classmethod
    def environment(cls, schema):
        return {**os.environ, 'WORKTIME_MIGRATION_URL': 'jdbc:mysql://127.0.0.1:' + cls.port + '/' + schema + '?serverTimezone=UTC&characterEncoding=UTF-8',
                'WORKTIME_MIGRATION_USER':'root', 'WORKTIME_MIGRATION_PASSWORD':cls.env['MYSQL_PWD'],
                'WORKTIME_MIGRATION_SERVER_UUID':cls.uuid,'WORKTIME_MIGRATION_DATABASE':schema}

    @classmethod
    def empty_schema(cls, schema):
        if not re.fullmatch('worktime_release_test_[a-z0-9_]+', schema): raise RuntimeError('unsafe test schema')
        exists = cls.sql(None, "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'").strip()
        if exists != b'0': raise RuntimeError('refuse existing test schema; use fresh disposable MySQL')
        cls.sql(None, 'CREATE DATABASE `' + schema + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.schema = 'worktime_release_test_' + self._testMethodName.removeprefix('test_')
        self.empty_schema(self.schema)
        p = subprocess.run([self.java, '-cp', self.cp, 'com.worktime.deploy.ReleaseFixture'],
                           env=self.environment(self.schema), capture_output=True, timeout=90)
        self.assertEqual(p.returncode, 0, 'synthetic V5 fixture preparation failed')
        self.runtime = LocalMysqlRuntime(self, self.schema)
        self.old = {'backend':'a'*40, 'web':'b'*40, 'migration_sha256':'c'*64}
        release.atomic(self.root / 'manifest.json', self.old)
        self.plan = {'target_sha':'d'*40, 'old_backend':'a'*40,'old_migration_sha256':'c'*64,
                     'migration_sha256':'e'*64,'archive_sha256':'f'*64,
                     'identity':{'server_uuid':self.uuid,'database':self.schema}}

    def tearDown(self): self.temp.cleanup()

    def test_success_and_restores(self):
        result = release.execute(self.root, self.plan, self.runtime)
        self.assertEqual(result['migration_sha256'], 'e'*64)
        state = release.load(self.root / release.JOURNAL)
        self.assertEqual(state['phase'], 'approved')
        self.assertEqual([x['version'] for x in state['snapshot']['history']], list('1234567'))

    def test_explicit_resume_after6(self):
        def interrupt(phase):
            if phase == 'verified6': raise RuntimeError('simulated loss')
        with self.assertRaises(RuntimeError): release.execute(self.root,self.plan,self.runtime,checkpoint=interrupt)
        with self.assertRaises(release.Refused): release.execute(self.root,self.plan,self.runtime)
        release.execute(self.root,self.plan,self.runtime,resume=True)
        self.assertEqual(self.runtime.observe()['history'][-1]['version'], '7')

    def test_corrupt_restore_blocks_migration(self):
        self.runtime.corrupt_restore = True
        with self.assertRaises(release.Refused): release.execute(self.root,self.plan,self.runtime)
        self.assertEqual(self.runtime.observe()['history'][-1]['version'], '5')
        self.assertEqual(release.load(self.root / 'manifest.json'), self.old)

    def test_db_identity_checked_before_ddl(self):
        with self.assertRaises(release.Refused):
            self.runtime.cli('migrate6',env_override={'WORKTIME_MIGRATION_SERVER_UUID':'wrong'})
        self.assertEqual(self.runtime.observe()['history'][-1]['version'],'5')

    def test_checksum_drift_refuses(self):
        self.sql(self.schema, "UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='1'")
        with self.assertRaises(release.Refused): release.execute(self.root,self.plan,self.runtime)
        self.assertFalse((self.root / release.JOURNAL).exists())

    def test_schema_index_drift_refuses(self):
        self.runtime.migrate(6)
        self.sql(self.schema, 'DROP INDEX idx_push_due ON push_deliveries')
        with self.assertRaises(release.Refused): self.runtime.observe()


if __name__ == '__main__': unittest.main()
