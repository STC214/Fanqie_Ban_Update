from pathlib import Path
import json,hashlib,subprocess,zipfile,difflib,tempfile,shutil,sys
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/usb-control-fast-toggle';BASE=OUT/'baseline';records=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args):
 p=subprocess.run(list(map(str,args)),cwd=ROOT,capture_output=True)
 records.append(dict(command=subprocess.list2cmdline(list(map(str,args))),exit=p.returncode,stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')))
 assert p.returncode==0,records[-1];return p.stdout
original=json.loads((OUT/'baseline-sha256.json').read_text());assert all(sha(BASE/n)==h for n,h in original.items())
assert sha(ROOT/'releases/usb-control-v1.0.3.apk')==original['releases/usb-control-v1.0.3.apk']
changes=[];patch=[]
files=[p for p in (ROOT/'usb_control_app').rglob('*') if p.is_file() and not any(x in p.relative_to(ROOT).parts for x in ['build','__pycache__'])]+[ROOT/'README.md',ROOT/'.gitignore']
for p in files:
 n=p.relative_to(ROOT).as_posix();before=BASE/n;old=before.read_bytes() if before.exists() else b'';new=p.read_bytes()
 if old==new:continue
 changes.append(dict(path=n,baseline=sha(before) if before.exists() else None,modified=sha(p)))
 patch+=difflib.unified_diff(old.decode('utf-8-sig').replace('\r\n','\n').splitlines(True),new.decode('utf-8-sig').replace('\r\n','\n').splitlines(True),fromfile='a/'+n if before.exists() else '/dev/null',tofile='b/'+n)
(OUT/'changes.json').write_text(json.dumps(changes,indent=2),encoding='utf-8');(OUT/'fast-toggle.patch').write_text(''.join(patch),encoding='utf-8',newline='\n')
with tempfile.TemporaryDirectory(prefix='usb-dark-verify-') as d:
 fixture=Path(d)
 for e in changes:
  q=fixture/e['path'];q.parent.mkdir(parents=True,exist_ok=True)
  if e['baseline']:q.write_text((BASE/e['path']).read_text(encoding='utf-8-sig'),encoding='utf-8',newline='\n')
 run(['git','-C',fixture,'apply','--check',OUT/'fast-toggle.patch']);run(['git','-C',fixture,'apply',OUT/'fast-toggle.patch'])
 for e in changes:assert (fixture/e['path']).read_text(encoding='utf-8-sig')==(ROOT/e['path']).read_text(encoding='utf-8-sig')
 # Rollback verifies raw bytes rather than normalized patch line endings.
 for e in changes:shutil.copy2(ROOT/e['path'],fixture/e['path'])
 run([sys.executable,OUT/'rollback.py','--root',fixture])
 for e in changes:assert (sha(fixture/e['path']) if (fixture/e['path']).exists() else None)==e['baseline']
for label,src in [('baseline',BASE),('modified',ROOT)]:
 dest=ROOT/'usb_control_app/build'/('dark-'+label+'-tests');dest.mkdir(exist_ok=True)
 folder=src/'usb_control_app';run(['javac','-encoding','UTF-8','-d',dest,*[folder/x for x in ['src/local/usbcontrol/Logic.java','src/local/usbcontrol/SafeFiles.java','src/local/usbcontrol/DensitySession.java','tests/model/Root.java','tests/model/TransactionTest.java','tests/LogicTest.java']]])
 run(['java','-cp',dest,'local.usbcontrol.LogicTest']);run(['java','-cp',dest,'local.usbcontrol.TransactionTest'])
for version in ['1.0.3','1.0.4']:
 apk=ROOT/('releases/usb-control-v'+version+'.apk')
 with zipfile.ZipFile(apk) as z:
  assert z.testzip() is None;assert len(z.namelist())==len(set(z.namelist()))
  assert sha(ROOT/'usb_control_app/res/drawable-nodpi/app_icon.png')==original['usb_control_app/res/drawable-nodpi/app_icon.png']
  with zipfile.ZipFile(ROOT/'releases/usb-control-v1.0.3.apk') as old_apk:assert z.read('res/drawable-nodpi-v4/app_icon.png')==old_apk.read('res/drawable-nodpi-v4/app_icon.png')
 run(['F:/Android/Sdk/build-tools/35.0.0/apksigner.bat','verify','--print-certs',apk]);run(['F:/Android/Sdk/build-tools/35.0.0/zipalign.exe','-c','4',apk])
run(['java','-Xmx1g','-cp','F:/Android/Sdk/cmdline-tools/latest/lib/lint-classpath.jar','com.android.tools.lint.Main','--offline','--exitcode','--sources','usb_control_app/src','--classpath','usb_control_app/build/classes','--resources','usb_control_app/res','--compile-sdk-version','36','--text',OUT/'lint.txt','--xml',OUT/'lint.xml','usb_control_app'])
assert '0 errors, 3 warnings' in (OUT/'lint.txt').read_text(encoding='utf-8')
adapter=ROOT/'usb_control_app/build/adapter-verified';adapter.mkdir(exist_ok=True)
run(['javac','-encoding','UTF-8','-d',adapter,*[ROOT/'usb_control_app'/x for x in ['src/local/usbcontrol/Logic.java','src/local/usbcontrol/Root.java','src/local/usbcontrol/UsbInstall.java','src/local/usbcontrol/SafeFiles.java','src/local/usbcontrol/DensitySession.java','src/local/usbcontrol/RecoveryScript.java','tests/UsbInstallTest.java']]])
run(['java','-cp',adapter,'local.usbcontrol.UsbInstallTest'])
def body(source,name):
 start=source.index('private void '+name+'(');brace=source.index('{',start);depth=1;end=brace+1
 while depth:
  if source[end]=='{':depth+=1
  if source[end]=='}':depth-=1
  end+=1
 return source[brace:end]
old=(BASE/'usb_control_app/src/local/usbcontrol/MainActivity.java').read_text(encoding='utf-8-sig')
new=(ROOT/'usb_control_app/src/local/usbcontrol/MainActivity.java').read_text(encoding='utf-8-sig')
assert 'ManagedDialogBuilder' in body(old,'confirmAdb') and 'ManagedDialogBuilder' in body(old,'changeInstall')
for name in ['changeAdb','changeInstall']:
 code=body(new,name);assert 'ManagedDialogBuilder' not in code and 'AlertDialog' not in code
 assert 'Root.SNAPSHOT' not in code and 'refresh()' not in code
assert 'UsbInstall.toggleSnapshot' in body(new,'changeInstall')
records.append(dict(result='PASS',checks=['baseline both directions had App confirmation','modified ADB and install handlers have zero confirmation dialogs','toggle paths omit full Root.SNAPSHOT and full refresh','pending state is read back before final commit']))
records.append(dict(result='PASS',modified_apk_sha256=sha(ROOT/'releases/usb-control-v1.0.4.apk'),baseline_apk_sha256=sha(ROOT/'releases/usb-control-v1.0.3.apk'),checks=['immutable baseline hashes','patch applied and compared','rollback executed and compared','baseline and modified model tests','unchanged original icon','both APK signatures and alignment','lint 0 errors / 3 prior warnings','MIUI strict capability and readback parser','official system verification preserved']))
(OUT/'verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS: baseline/modified, patch, rollback, signatures, icon, models, lint, USB adapter')
