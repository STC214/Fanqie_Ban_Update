"""Restore exactly the preserved 1.0.1 source tree; no phone or system changes."""
from pathlib import Path
import argparse,json,hashlib,shutil
p=argparse.ArgumentParser();p.add_argument('--root',required=True);a=p.parse_args()
HERE=Path(__file__).resolve().parent;root=Path(a.root).resolve();manifest=json.loads((HERE/'changes.json').read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
for entry in manifest:
 target=(root/entry['path']).resolve();assert root in target.parents,'Target leaves root'
 assert sha(target)==entry['modified'],'Modified file differs: '+entry['path']
 if entry['baseline']:assert sha(HERE/'baseline'/entry['path'])==entry['baseline'],'Baseline damaged'
for entry in manifest:
 target=root/entry['path']
 if entry['baseline']:
  target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(HERE/'baseline'/entry['path'],target)
 else:target.unlink()
 assert sha(target)==entry['baseline']
print('PASS: exact baseline source restored; no phone settings changed')
