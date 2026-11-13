"""Verify the current release without archived historical APKs."""
from pathlib import Path
import subprocess, hashlib, json, zipfile, xml.etree.ElementTree as ET, argparse

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--out', default='.local/latest-release-verification')
args = parser.parse_args()
OUT = ROOT / args.out
OUT.mkdir(parents=True, exist_ok=True)
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
    assert z.read('res/raw/wallpaper.sh') == (app / 'res/raw/wallpaper.sh').read_bytes(), 'APK helper differs from tested source'
    dex = z.read('classes.dex')
    assert b'Llocal/usbcontrol/WallpaperLoop;' in dex, 'Wallpaper wrapper missing from DEX'
run(['F:/Android/Sdk/build-tools/35.0.0/apksigner.bat', 'verify', '--print-certs', apk])
run(['F:/Android/Sdk/build-tools/35.0.0/zipalign.exe', '-c', '4', apk])
assert ("versionName='" + version + "'").encode() in run(['F:/Android/Sdk/build-tools/35.0.0/aapt2.exe', 'dump', 'badging', apk])
for label, sources, mains in [
    ('model', ['src/local/usbcontrol/Logic.java', 'src/local/usbcontrol/SafeFiles.java',
               'src/local/usbcontrol/DensitySession.java', 'tests/model/Root.java',
               'tests/model/TransactionTest.java', 'tests/LogicTest.java'], ['LogicTest', 'TransactionTest']),
    ('system-apps', ['src/local/usbcontrol/SystemApps.java', 'tests/SystemAppsTest.java'], ['SystemAppsTest']),
    ('adapter', ['src/local/usbcontrol/Logic.java', 'src/local/usbcontrol/Root.java',
                 'src/local/usbcontrol/UsbInstall.java', 'src/local/usbcontrol/SafeFiles.java',
                 'src/local/usbcontrol/DensitySession.java', 'src/local/usbcontrol/RecoveryScript.java',
                 'tests/UsbInstallTest.java'], ['UsbInstallTest'])]:
    dest = app / 'build' / ('latest-' + label)
    dest.mkdir(exist_ok=True, parents=True)
    run(['javac', '-encoding', 'UTF-8', '-d', dest, *[app / x for x in sources]])
    for main in mains:
        run(['java', '-cp', dest, 'local.usbcontrol.' + main])
# One explicit previous release is intentionally retained for this migration.
allowed_names = {'usb-control-v' + v + '.apk' + suffix
                 for v in (version, '1.0.8') for suffix in ('', '.sha256', '.idsig')}
old = [p.relative_to(ROOT).as_posix() for p in (ROOT / 'releases').glob('usb-control-v*.apk*')
       if p.name not in allowed_names]
assert not old, old
manifest = json.loads((ROOT / 'docs/release-cleanup/manifest.json').read_text(encoding='utf-8-sig'))
assert all(not (ROOT / e['path']).exists() for e in manifest['files'])
records.append(dict(result='PASS', version=version, apk_sha256=digest, archived_files=len(manifest['files']),
                    checks=['latest signature/alignment/version/checksum/ZIP', 'current model and adapter tests',
                            'only current release and explicit 1.0.8 rollback baseline in releases']))
(OUT / 'verification.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: latest release, tests and historical-package cleanup')
