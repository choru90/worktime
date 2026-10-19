#!/usr/bin/env python3
import hashlib,json,os
from pathlib import Path
service=os.environ['SERVICE'];sha=os.environ['GITHUB_SHA']
from migrations import fingerprint
migration_hash=fingerprint(Path('.'))
archive=Path('image.tar.gz')
with archive.open('rb') as f: digest=hashlib.file_digest(f,'sha256').hexdigest()
Path('metadata.json').write_text(json.dumps(dict(service=service,sha=sha,archive_sha256=digest,migration_sha256=migration_hash)))
