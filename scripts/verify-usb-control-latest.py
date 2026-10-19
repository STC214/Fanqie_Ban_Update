"""Verify the current release without archived historical APKs."""
from pathlib import Path
import subprocess, hashlib, json, zipfile, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/release-cleanup'
records = []

def run(args):
    p = subprocess.run(list(map(str, args)), cwd=ROOT, capture_output=True)
    records.append(dict(command=subprocess.list2cmdline(list(map(str, args))), exit=p.returncode,
                        stdout=p.stdout.decode('utf-8', 'replace'), stderr=p.stderr.decode('utf-8', 'replace')))
    assert p.returncode == 0, records[-1]
    return p.stdout

app = ROOT / 'usb_control_app'
attrs = ET.parse(app / 'AndroidManifest.xml').getroot().attrib
version = attrs['{http://schemas.android.com/apk/res/android}versionName']
apk = ROOT / ('releases/usb-control-v' + version + '.apk')
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
assert digest.upper() == Path(str(apk) + '.sha256').read_text().split()[0].upper()
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
run(['F:/Android/Sdk/build-tools/35.0.0/apksigner.bat', 'verify', '--print-certs', apk])
run(['F:/Android/Sdk/build-tools/35.0.0/zipalign.exe', '-c', '4', apk])
assert ("versionName='" + version + "'").encode() in run(['F:/Android/Sdk/build-tools/35.0.0/aapt2.exe', 'dump', 'badging', apk])
for label, sources, mains in [
    ('model', ['src/local/usbcontrol/Logic.java', 'src/local/usbcontrol/SafeFiles.java',
               'src/local/usbcontrol/DensitySession.java', 'tests/model/Root.java',
               'tests/model/TransactionTest.java', 'tests/LogicTest.java'], ['LogicTest', 'TransactionTest']),
    ('adapter', ['src/local/usbcontrol/Logic.java', 'src/local/usbcontrol/Root.java',
                 'src/local/usbcontrol/UsbInstall.java', 'src/local/usbcontrol/SafeFiles.java',
                 'src/local/usbcontrol/DensitySession.java', 'src/local/usbcontrol/RecoveryScript.java',
                 'tests/UsbInstallTest.java'], ['UsbInstallTest'])]:
    dest = app / 'build' / ('latest-' + label)
    dest.mkdir(exist_ok=True, parents=True)
    run(['javac', '-encoding', 'UTF-8', '-d', dest, *[app / x for x in sources]])
    for main in mains:
        run(['java', '-cp', dest, 'local.usbcontrol.' + main])
old = [p.relative_to(ROOT).as_posix() for p in ROOT.rglob('usb-control-v*.apk*')
       if '.git' not in p.relative_to(ROOT).parts and '.local' not in p.relative_to(ROOT).parts
       and not p.name.startswith('usb-control-v' + version + '.apk')]
assert not old, old
manifest = json.loads((OUT / 'manifest.json').read_text(encoding='utf-8-sig'))
assert all(not (ROOT / e['path']).exists() for e in manifest['files'])
records.append(dict(result='PASS', version=version, apk_sha256=digest, archived_files=len(manifest['files']),
                    checks=['latest signature/alignment/version/checksum/ZIP', 'current model and adapter tests',
                            'no historical USB Control packages in project']))
(OUT / 'verification.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: latest release, tests and historical-package cleanup')
