"""Restore 1.0.6 source/release only; deleted Android app data is not recoverable here."""
from pathlib import Path
import argparse, hashlib, json, zipfile

PROJECT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--target-root', type=Path, default=PROJECT)
args = parser.parse_args()
root = args.target_root.resolve()
archive = PROJECT / 'docs/usb-control-system-apps-delete-data/rollback-originals.zip'
def sha(data): return hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(archive) as z:
    manifest = json.loads(z.read('manifest.json'))
    for item in manifest:
        target = (root / item['path']).resolve()
        assert target.is_relative_to(root), 'Path leaves rollback root'
        if target.exists():
            assert sha(target.read_bytes()) in (item['original'], item['modified']), f'File changed: {target}'
        if item['original']:
            assert sha(z.read(item['path'])) == item['original'], 'Original checksum mismatch'
    for item in manifest:
        target = root / item['path']
        if item['original']:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(z.read(item['path']))
            assert sha(target.read_bytes()) == item['original']
        elif target.exists():
            target.unlink()
print('PASS: 1.0.6 source/release restored with checksum verification; Android app data not restored')
