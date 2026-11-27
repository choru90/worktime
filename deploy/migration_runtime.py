"""Docker adapter for the reviewed V5→V7 release. No provisioning or secret export."""
import json
from pathlib import Path
import re
import subprocess

from migration_release import Refused, require, private_file, digest, object_digest, sync


class DockerRuntime:
    def __init__(self, root, plan, incoming):
        self.root, self.plan = root, plan
        self.config = plan['docker']
        c = self.config
        for key in ['source_container', 'restore_container']:
            require(re.fullmatch('[a-f0-9]{64}', c[key]), 'exact container IDs required')
        require(c['source_container'] != c['restore_container'], 'restore must use a separate MySQL instance')
        require(re.fullmatch('sha256:[a-f0-9]{64}', c['image_id']), 'exact target image ID required')
        for db in [plan['identity']['database'], *c['restore_databases'].values()]:
            require(re.fullmatch('[a-z][a-z0-9_]{0,63}', db), 'invalid approved schema')
        require(set(c['restore_databases']) == {'v5', 'v7'}
                and len(set(c['restore_databases'].values())) == 2, 'separate empty restore schemas required')
        for key in ['source_env', 'restore_v5_env', 'restore_v7_env']:
            path = root / c[key]
            require(path.parent == root and path.name == c[key], 'credential file outside trusted root')
            private_file(path)
        self.env_hashes = {key: digest(root / c[key]) for key in ['source_env', 'restore_v5_env', 'restore_v7_env']}
        require(c['writer_containers'] and len(set(c['writer_containers'])) == len(c['writer_containers']), 'writer inventory required')
        for cid in c['writer_containers']:
            require(re.fullmatch('[a-f0-9]{64}', cid), 'exact writer container IDs required')
        self.run(['docker', 'load', '-i', str(incoming / 'image.tar.gz')])
        image = json.loads(self.run(['docker', 'image', 'inspect', 'worktime-backend:' + plan['target_sha']]))
        require(len(image) == 1 and image[0]['Id'] == c['image_id'], 'loaded target image identity mismatch')
        self.inventory(None)

    def run(self, args, data=None):
        try:
            p = subprocess.run(args, input=data, capture_output=True, cwd=self.root, timeout=900)
        except (OSError, subprocess.SubprocessError):
            raise Refused('Docker/DB operation failed; sensitive output suppressed') from None
        require(p.returncode == 0, 'Docker/DB operation failed; sensitive output suppressed')
        require(len(p.stdout) <= 268435456, 'operation output exceeds reviewed capacity')
        return p.stdout

    def inventory(self, frozen):
        c = self.config
        ids = self.run(['docker', 'ps', '-aq']).decode().split()
        require(bool(ids), 'container inventory empty')
        containers = json.loads(self.run(['docker', 'inspect', *ids]))
        by_id = {x['Id']: x for x in containers}
        for name in ['source_container', 'restore_container']:
            row = by_id.get(c[name], {})
            require(row.get('State', {}).get('Running') is True
                    and row.get('Config', {}).get('Image', '').startswith('mysql'), 'approved MySQL instance unavailable')
        for cid in c['writer_containers']:
            row = by_id.get(cid, {})
            require(row.get('Config', {}).get('Image') == 'worktime-backend:' + self.plan['old_backend'], 'writer image changed')
            if frozen is not None:
                require(bool(row.get('State', {}).get('Running')) is not frozen, 'writer state changed')
            require(row.get('HostConfig', {}).get('RestartPolicy', {}).get('Name') in {'no', ''},
                    'writer auto-restart must be disabled through reviewed maintenance before migration')
        # Catch additional backend containers, including stopped/restarting instances.
        known = set(c['writer_containers'])
        for row in containers:
            image = row.get('Config', {}).get('Image', '')
            if 'worktime-backend' in image:
                require(row['Id'] in known, 'unreviewed backend instance present')
        # An operator-reviewed external writer freeze is mandatory; this does not
        # pretend Docker inventory can prove the absence of off-host writers.
        require(c.get('external_writers_frozen') is True, 'external writer maintenance acknowledgement required')

    def freeze(self):
        self.inventory(False)
        self.run(['docker', 'stop', '--time', '30', *self.config['writer_containers']])
        self.assert_frozen()

    def assert_frozen(self):
        self.inventory(True)

    def cli(self, action, label=None):
        c = self.config
        cid = c['source_container'] if label is None else c['restore_container']
        env = c['source_env'] if label is None else c['restore_' + label + '_env']
        env_key = 'source_env' if label is None else 'restore_' + label + '_env'
        private_file(self.root / env)
        require(digest(self.root / env) == self.env_hashes[env_key], 'migration credential/target file changed')
        raw = self.run(['docker', 'run', '--rm', '--network', 'container:' + cid,
                        '--env-file', str(self.root / env), '--entrypoint', 'java', c['image_id'],
                        '-Dloader.main=com.worktime.deploy.MigrationCli', '-cp', '/app/app.jar',
                        'org.springframework.boot.loader.launch.PropertiesLauncher', action])
        lines = [x[len(b'WORKTIME_MIGRATION_JSON='):] for x in raw.splitlines()
                 if x.startswith(b'WORKTIME_MIGRATION_JSON=')]
        require(len(lines) == 1, 'missing migration CLI result')
        result = json.loads(lines[0])
        expected = self.plan['identity'] if label is None else {
            'server_uuid': c['restore_server_uuid'], 'database': c['restore_databases'][label]}
        require(result['identity'] == expected, 'JDBC target identity mismatch')
        return result

    def observe(self):
        return self.cli('snapshot')

    def migrate(self, version):
        require(version in (6, 7), 'only reviewed additive migration targets allowed')
        self.cli('migrate' + str(version))

    def mysql(self, source, database, sql=None, dump=False, data=None):
        cid = self.config['source_container' if source else 'restore_container']
        # Static shell only; credentials are resolved inside the approved container.
        auth = ('set -eu; if [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ]; then '
                'export MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; else '
                'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi; exec "$@"')
        command = ['mysqldump', '--single-transaction', '--quick', '--hex-blob', '--routines', '--triggers', '--events',
                   '--set-gtid-purged=OFF', '--no-tablespaces', '--column-statistics=0', '--skip-comments', '--skip-dump-date',
                   '--default-character-set=utf8mb4', '-uroot', database] if dump else [
                   'mysql', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '-uroot', database]
        return self.run(['docker', 'exec', '-i', cid, 'sh', '-c', auth, 'migration-db', *command],
                        sql.encode() if sql is not None else data)

    def backup_restore(self, root, label, snapshot):
        self.assert_frozen()
        source = self.plan['identity']['database']
        restore = self.config['restore_databases'][label]
        identity = self.mysql(False, restore, 'SELECT @@server_uuid,DATABASE(); SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').decode().splitlines()
        require(identity == [self.config['restore_server_uuid'] + '\t' + restore, '0'], 'restore target not approved and empty')
        raw = self.mysql(True, source, dump=True)
        require(raw and not re.search(rb'(?im)^\s*(?:CREATE|DROP)\s+DATABASE|^\s*USE\s', raw), 'dump contains database switching statements')
        path = root / ('migration-' + label + '.sql')
        # Never overwrite a prior backup; repeat requires reviewed fresh restore targets/backup location.
        import os
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'wb') as out:
            out.write(raw); out.flush(); os.fsync(out.fileno())
        sync(root)
        require(path.read_bytes() == raw, 'backup write verification failed')
        self.mysql(False, restore, data=raw)
        restored = self.cli('snapshot', label)
        restored['identity'] = snapshot['identity']
        require(restored == snapshot, 'backup restore snapshot mismatch')
        self.assert_frozen()
        return {'file': path.name, 'sha256': digest(path), 'snapshot_sha256': object_digest(snapshot)}
