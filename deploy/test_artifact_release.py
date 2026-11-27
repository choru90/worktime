import unittest
import os
from pathlib import Path
import tempfile
import runpy
import hashlib
import json
from unittest.mock import patch
from artifact_release import validate_mode, validate_prepared
from migration_release import Refused


class ArtifactReleaseTest(unittest.TestCase):
    def test_metadata_adds_verified_image_id_without_changing_existing_fields(self):
        script=Path(__file__).resolve().parent/'package.py'
        cwd=Path.cwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                Path('image.tar.gz').write_bytes(b'synthetic archive')
                with patch.dict(os.environ,{'SERVICE':'backend','GITHUB_SHA':'a'*40}), \
                     patch('subprocess.check_output',return_value='sha256:'+'b'*64+'\n') as inspect:
                    runpy.run_path(str(script),run_name='__main__')
                    inspect.assert_called_once_with(['docker','image','inspect','--format','{{.Id}}','worktime-backend:'+'a'*40],text=True)
                result=json.loads(Path('metadata.json').read_text())
                self.assertEqual(result,{'service':'backend','sha':'a'*40,
                    'archive_sha256':hashlib.sha256(b'synthetic archive').hexdigest(),
                    'migration_sha256':hashlib.sha256(b'').hexdigest(),'image_id':'sha256:'+'b'*64})
            finally:
                os.chdir(cwd)

    def test_workflow_prepare_never_enters_deploy_and_provenance_precedes_download(self):
        source=(Path(__file__).resolve().parent.parent/'.github/workflows/staging.yml').read_text()
        deploy=source.split('\n  deploy:\n',1)[1]
        guard=deploy.split('\n    needs:',1)[0]
        self.assertIn("github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.deploy_staging",guard)
        self.assertNotIn('inputs.prepare_artifact',guard)
        self.assertLess(deploy.index('deploy/artifact_release.py provenance'),deploy.index('actions/download-artifact@'))
        self.assertLess(deploy.index('actions/download-artifact@'),deploy.index('bash deploy/upload.sh'))

    def test_prepare_has_no_deployment_and_inputs_cannot_be_combined(self):
        validate_mode(False,True,'','refs/heads/main')
        validate_mode(True,False,'123','refs/heads/main')
        for args in [(True,True,'','refs/heads/main'),(False,False,'123','refs/heads/main'),
                     (False,True,'123','refs/heads/main'),(True,False,'../other','refs/heads/main'),
                     (False,True,'','refs/heads/feature')]:
            with self.assertRaises(Refused): validate_mode(*args)

    def test_prepared_artifact_requires_successful_same_main_workflow_revision(self):
        valid={'status':'completed','conclusion':'success','event':'workflow_dispatch','head_branch':'main',
               'head_sha':'a'*40,'path':'.github/workflows/staging.yml'}
        validate_prepared(valid,'a'*40)
        for key,value in [('status','in_progress'),('conclusion','failure'),('event','pull_request'),
                          ('head_branch','feature'),('head_sha','b'*40),('path','.github/workflows/other.yml')]:
            with self.assertRaises(Refused): validate_prepared({**valid,key:value},'a'*40)


if __name__ == '__main__': unittest.main()
