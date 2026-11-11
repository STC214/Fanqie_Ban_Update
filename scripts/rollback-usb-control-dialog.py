"""Restore the pre-dialog-UI source and release from checksummed originals."""
from pathlib import Path
import argparse,hashlib,json,zipfile
PROJECT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--target-root',type=Path,default=PROJECT)
args=parser.parse_args();root=args.target_root.resolve()
sha=lambda data:hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(PROJECT/'docs/usb-control-system-apps-dialog/rollback-originals.zip') as z:
    manifest=json.loads(z.read('manifest.json'))
    for item in manifest:
        target=(root/item['path']).resolve()
        assert target.is_relative_to(root),'Rollback path leaves root'
        if target.exists():assert sha(target.read_bytes()) in [item['original'],item['modified']],f'File changed: {target}'
        if item['original']:assert sha(z.read(item['path']))==item['original'],'Original checksum mismatch'
    for item in manifest:
        target=root/item['path']
        if item['original']:
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(item['path']))
            assert sha(target.read_bytes())==item['original']
        elif target.exists():target.unlink()
print('PASS: pre-dialog source/release restored and all original hashes verified')
print('Phone rollback is separate: adb install -r -d releases/usb-control-v1.0.7.apk')
