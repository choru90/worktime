import json
import unittest
import tempfile
import os
import stat
from unittest.mock import patch
from pathlib import Path
from migration_runtime import DockerRuntime, approved_environment
from migration_release import Refused
from migration_release import private_file, trusted_file


class InventoryTest(unittest.TestCase):
    def test_existing_nonroot_executor_reads_trusted_code_and_owns_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'credential.env'
            path.write_text('synthetic')
            path.chmod(0o600)
            private_file(path)
            path.chmod(0o640)
            with self.assertRaises(Refused): private_file(path)
            info=list(path.stat())
            info[0]=stat.S_IFREG | 0o640
            # Administrator-owned, group-readable to the existing executor.
            info[4]=0
            # stat_result positions: nlink=3, uid=4; preserve one hard link
            info[3]=1
            with patch.object(Path,'lstat',return_value=os.stat_result(info)):
                trusted_file(path)
            info[0]=stat.S_IFREG | 0o660
            with patch.object(Path,'lstat',return_value=os.stat_result(info)):
                with self.assertRaises(Refused): trusted_file(path)

    def test_jdbc_target_is_local_and_matches_approved_db_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'source.env'
            valid=('WORKTIME_MIGRATION_URL=jdbc:mysql://127.0.0.1:3306/source?serverTimezone=UTC&characterEncoding=UTF-8\n'
                   'WORKTIME_MIGRATION_USER=approved\nWORKTIME_MIGRATION_PASSWORD=synthetic\n'
                   'WORKTIME_MIGRATION_SERVER_UUID=uuid\nWORKTIME_MIGRATION_DATABASE=source\n')
            path.write_text(valid)
            approved_environment(path,{'server_uuid':'uuid','database':'source'})
            for raw in [valid.replace('127.0.0.1','other-host'),valid.replace('UUID=uuid','UUID=other'),valid+'UNREVIEWED_SETTING=yes\n',valid+'WORKTIME_MIGRATION_USER=duplicate\n']:
                path.write_text(raw)
                with self.assertRaises(Refused): approved_environment(path,{'server_uuid':'uuid','database':'source'})

    def runtime(self):
        r = object.__new__(DockerRuntime)
        r.plan = {'old_backend':'a'*40}
        r.config = {'source_container':'1'*64,'restore_container':'2'*64,'writer_containers':['3'*64], 'external_writers_frozen':True}
        self.rows = [
            {'Id':'1'*64,'Config':{'Image':'mysql:8'},'State':{'Running':True}},
            {'Id':'2'*64,'Config':{'Image':'mysql:8'},'State':{'Running':True}},
            {'Id':'3'*64,'Config':{'Image':'worktime-backend:'+'a'*40},'State':{'Running':False},'HostConfig':{'RestartPolicy':{'Name':'no'}}}]
        r.run = lambda args: b'ids' if args[1] == 'ps' else json.dumps(self.rows).encode()
        return r

    def test_frozen_inventory_and_unknown_backend(self):
        r = self.runtime()
        r.inventory(True)
        self.rows.append({'Id':'4'*64,'Config':{'Image':'worktime-backend:unknown'},'State':{'Running':False}})
        with self.assertRaises(Refused): r.inventory(True)

    def test_restart_policy_running_writer_and_external_freeze(self):
        r = self.runtime()
        self.rows[-1]['HostConfig']['RestartPolicy']['Name'] = 'always'
        with self.assertRaises(Refused): r.inventory(True)
        self.rows[-1]['HostConfig']['RestartPolicy']['Name'] = 'no'
        self.rows[-1]['State']['Running'] = True
        with self.assertRaises(Refused): r.inventory(True)
        r.config['external_writers_frozen'] = False
        with self.assertRaises(Refused): r.inventory(None)


if __name__ == '__main__': unittest.main()
