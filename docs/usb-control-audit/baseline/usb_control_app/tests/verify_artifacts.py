from pathlib import Path
import difflib, hashlib, json, shutil, subprocess, tempfile, zipfile

root=Path(__file__).resolve().parents[2]
audit=root/'docs/usb-control-app'
hashof=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
records=[]
def run(args,cwd=root):
    p=subprocess.run([str(x) for x in args],cwd=cwd,capture_output=True)
    records.append(dict(command=subprocess.list2cmdline([str(x) for x in args]),cwd=str(cwd),exit=p.returncode,stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')))
    assert p.returncode==0,records[-1]
    return p.stdout
text=(root/'README.md').read_text(encoding='utf-8')
start=text.index('## ',text.index('2.1.3**'))
end=text.index('## ',start+3)
baseline=text[:start]+text[end:]
baseline=''.join(line for line in baseline.splitlines(True) if not line.startswith('usb_control_app/'))
expected=json.loads((root/'docs/reorganization/manifest.json').read_text(encoding='utf-8'))['readme_sha256']
assert hashlib.sha256(baseline.replace('\n','\r\n').encode('utf-8')).hexdigest()==expected
(audit/'baseline').mkdir(exist_ok=True)
(audit/'baseline/README.md').write_text(baseline,encoding='utf-8')
sources=sorted(p for p in (root/'usb_control_app').rglob('*') if p.is_file() and 'build' not in p.relative_to(root/'usb_control_app').parts and p.suffix not in ['.png','.pyc'] and '__pycache__' not in p.parts)
sources += [root/'scripts/rollback-usb-control-app.ps1']
patch=list(difflib.unified_diff(baseline.splitlines(True),text.splitlines(True),fromfile='a/README.md',tofile='b/README.md'))
for p in sources:
    rel=p.relative_to(root).as_posix()
    patch.append('diff --git a/'+rel+' b/'+rel+'\nnew file mode 100644\n')
    patch.extend(difflib.unified_diff([],p.read_text(encoding='utf-8-sig').splitlines(True),fromfile='/dev/null',tofile='b/'+rel))
(audit/'source.patch').write_text(''.join(patch),encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='usb-app-patch-') as temp:
    fixture=Path(temp);(fixture/'README.md').write_text(baseline,encoding='utf-8')
    run(['git','apply','--check',audit/'source.patch'],fixture)
    run(['git','apply',audit/'source.patch'],fixture)
    for p in sources:
        rel=p.relative_to(root)
        assert (fixture/rel).read_text(encoding='utf-8')==p.read_text(encoding='utf-8-sig')
apk=root/'releases/usb-control-v1.0.0.apk'
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert 'classes.dex' in z.namelist()
assert hashof(root/'147263862_p0_master1200.png')==hashof(root/'usb_control_app/res/drawable-nodpi/app_icon.png')
manifest=dict(apk=apk.relative_to(root).as_posix(),bytes=apk.stat().st_size,sha256=hashof(apk),icon_sha256=hashof(root/'147263862_p0_master1200.png'),sources={p.relative_to(root).as_posix():hashof(p) for p in sources})
(audit/'artifacts.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
run(['javac','-encoding','UTF-8','-d','usb_control_app/build/tests','usb_control_app/src/local/usbcontrol/Logic.java','usb_control_app/tests/LogicTest.java'])
run(['java','-cp','usb_control_app/build/tests','local.usbcontrol.LogicTest'])
run(['powershell','-NoProfile','-Command',"$t=$null;$e=$null;[void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts/rollback-usb-control-app.ps1'),[ref]$t,[ref]$e);if($e.Count){throw ($e|Out-String)};'Rollback script syntax: PASS'"])
run(['cmd','/d','/c',r'F:\Android\Sdk\build-tools\35.0.0\apksigner.bat','verify','--verbose',apk])
run([r'F:\Android\Sdk\build-tools\35.0.0\zipalign.exe','-c','4',apk])
run([r'F:\Android\Sdk\build-tools\35.0.0\aapt.exe','dump','badging',apk])
run(['git','diff','--check'])
records.append(dict(check='APK CRC, original icon hash, baseline README preserved, source patch applied and contents compared',result='PASS'))
(audit/'artifact-verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: APK signatures/alignment/CRC, icon hash, 19 unit assertions, patch apply and source contents, rollback syntax')
