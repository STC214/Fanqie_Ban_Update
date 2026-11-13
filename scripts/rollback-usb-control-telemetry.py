"""Restore checksummed pre-1.0.9 source files; no phone or APK mutations."""
from pathlib import Path
import argparse, hashlib, json, tempfile, zipfile, shutil

PROJECT = Path(__file__).resolve().parents[1]
BUNDLE = PROJECT / 'docs/usb-control-telemetry-review/rollback-originals.zip'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def restore(root, apply):
    with zipfile.ZipFile(BUNDLE) as z:
        assert z.testzip() is None
        entries = json.loads(z.read('manifest.json'))
        for e in entries:
            target = (root/e['path']).resolve()
            assert target.is_relative_to(root.resolve()), 'Path leaves target root'
            if e.get('original'):
                assert sha(z.read(e['path'])) == e['original'], 'Original hash mismatch'
            current = sha(target.read_bytes()) if target.is_file() else None
            assert current in (e.get('original'), e['modified']), f'Later edit detected: {target}'
        if apply:
            for e in entries:
                target = root/e['path']
                if e.get('original'):
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(e['path']))
                    assert sha(target.read_bytes()) == e['original']
                elif target.exists():
                    target.unlink()
                    assert not target.exists()
    return len(entries)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    modes = p.add_mutually_exclusive_group()
    modes.add_argument('--check', action='store_true')
    modes.add_argument('--apply', action='store_true')
    modes.add_argument('--self-test', action='store_true')
    p.add_argument('--target-root', type=Path, default=PROJECT)
    args = p.parse_args()
    root = args.target_root.resolve()
    if args.self_test:
        with tempfile.TemporaryDirectory(prefix='qk-telemetry-rollback-') as d:
            dest = Path(d)
            with zipfile.ZipFile(BUNDLE) as z:
                for e in json.loads(z.read('manifest.json')):
                    source = root/e['path']
                    if source.is_file():
                        target = dest/e['path']
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source, target)
            count = restore(dest, True)
        print(f'PASS: {count} source files restored in temporary tree; workspace and phone untouched')
    else:
        count = restore(root, args.apply)
        print(f'PASS: {count} source files; '+('rollback applied' if args.apply else 'read-only preflight'))
