"""Fingerprint SQL and JVM Flyway migrations, including V4 Kotlin migration."""
import hashlib
from pathlib import Path

def files(root):
    found=[]
    for directory in ('src/main/resources/db/migration','src/main/kotlin/db/migration','src/main/java/db/migration'):
        folder=root/directory
        if folder.exists():found.extend(p for p in folder.rglob('*') if p.is_file())
    return sorted(found)

def fingerprint(root):
    h=hashlib.sha256()
    for p in files(root):
        h.update(p.relative_to(root).as_posix().encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    return h.hexdigest()

if __name__=='__main__':
    import sys
    root=Path('.')
    if '--count' in sys.argv:print(sum(1 for p in files(root) if p.name.startswith('V') and p.suffix in ('.sql','.kt','.java')))
    else:print(fingerprint(root))
