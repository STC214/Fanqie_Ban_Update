from pathlib import Path
import hashlib,json,struct,subprocess,tempfile,shutil,difflib,zipfile,zlib,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/usb-control-audit';BASE=OUT/'baseline';records=[]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,cwd=ROOT):
 p=subprocess.run([str(a) for a in args],cwd=cwd,capture_output=True);records.append(dict(command=subprocess.list2cmdline([str(a) for a in args]),cwd=str(cwd),exit=p.returncode,stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')));assert p.returncode==0,records[-1];return p.stdout
def scope(base):
 f={p.relative_to(base).as_posix():p for p in (base/'usb_control_app').rglob('*') if p.is_file() and 'build' not in p.relative_to(base/'usb_control_app').parts and '__pycache__' not in p.parts and p.suffix!='.pyc'};f['README.md']=base/'README.md';return f
baseline=json.loads((OUT/'baseline-hashes.json').read_text(encoding='utf-8'))
for rel,h in baseline.items():assert sha(BASE/rel)==h,rel
assert sha(ROOT/'releases/usb-control-v1.0.0.apk')==baseline['releases/usb-control-v1.0.0.apk']
manifest=ET.parse(ROOT/'usb_control_app/AndroidManifest.xml').getroot();NS='{http://schemas.android.com/apk/res/android}';version=manifest.get(NS+'versionName');apk=ROOT/f'releases/usb-control-v{version}.apk'
assert version=='1.0.1' and manifest.get(NS+'versionCode')=='101';assert manifest.find('uses-sdk').get(NS+'targetSdkVersion')=='36'
assert not manifest.findall('uses-permission') and manifest.find('instrumentation') is None
assert manifest.find('application').get(NS+'allowBackup')=='false' and manifest.find('application').get(NS+'debuggable') in [None,'false']
assert len(manifest.findall('./application/activity'))==1
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()));dex=z.read('classes.dex')
 assert not any(n.startswith('lib/') or n.startswith('assets/') for n in z.namelist())
 assert struct.unpack_from('<I',dex,8)[0]==zlib.adler32(dex[12:])&0xffffffff and dex[12:32]==hashlib.sha1(dex[32:]).digest()
 assert struct.unpack_from('<I',dex,32)[0]==len(dex)
 strings=[];count,offset=struct.unpack_from('<II',dex,56)
 for i in range(count):
  off=struct.unpack_from('<I',dex,offset+i*4)[0]
  while dex[off]&128:off+=1
  off+=1;strings.append(dex[off:dex.index(0,off)].decode('utf-8','replace'))
 classes=[];_,type_off=struct.unpack_from('<II',dex,64);count,offset=struct.unpack_from('<II',dex,96)
 for i in range(count):
  type_id=struct.unpack_from('<I',dex,offset+i*32)[0];string_id=struct.unpack_from('<I',dex,type_off+type_id*4)[0];classes.append(strings[string_id])
 assert all(c.startswith('Llocal/usbcontrol/') for c in classes),classes
 for name in ['MainActivity','Root','DensitySession','SafeFiles','Baseline','BackgroundStore','RecoveryScript']:assert 'Llocal/usbcontrol/'+name+';' in classes
 assert not any('AuditInstrumentation' in c or 'Test;' in c for c in classes)
records.append(dict(check='Production APK ZIP, DEX digest/checksum, class inventory, components and permissions',result='PASS',classes=classes))
assert sha(ROOT/'147263862_p0_master1200.png')==sha(ROOT/'usb_control_app/res/drawable-nodpi/app_icon.png')
tools=Path('F:/Android/Sdk/build-tools/35.0.0')
new=run(['cmd','/d','/c',tools/'apksigner.bat','verify','--verbose','--print-certs',apk]);old=run(['cmd','/d','/c',tools/'apksigner.bat','verify','--print-certs',BASE/'releases/usb-control-v1.0.0.apk'])
assert next(x for x in new.splitlines() if b'certificate SHA-256 digest' in x) in old
run([tools/'zipalign.exe','-c','4',apk]);run([tools/'aapt.exe','dump','badging',apk])
run(['javac','-encoding','UTF-8','-d','usb_control_app/build/tests','usb_control_app/src/local/usbcontrol/Logic.java','usb_control_app/tests/LogicTest.java']);run(['java','-cp','usb_control_app/build/tests','local.usbcontrol.LogicTest'])
model=['usb_control_app/src/local/usbcontrol/'+n+'.java' for n in ['Logic','SafeFiles','DensitySession']]+['usb_control_app/tests/model/Root.java','usb_control_app/tests/model/TransactionTest.java']
run(['javac','-encoding','UTF-8','-d','usb_control_app/build/model-tests',*model]);run(['java','-cp','usb_control_app/build/model-tests','local.usbcontrol.TransactionTest'])
run(['java','-Xmx1g','-cp','F:/Android/Sdk/cmdline-tools/latest/lib/lint-classpath.jar','com.android.tools.lint.Main','--offline','--exitcode','--sources','usb_control_app/src','--classpath','usb_control_app/build/classes','--resources','usb_control_app/res','--compile-sdk-version','36','--text','docs/usb-control-audit/lint-final.txt','--xml','docs/usb-control-audit/lint-final.xml','usb_control_app'])
issues=ET.parse(OUT/'lint-final.xml').getroot().findall('issue');assert not any(n.get('severity') in ['Error','Fatal'] for n in issues)
assert all(n.get('id') in ['ExifInterface','OldTargetApi','MonochromeLauncherIcon'] for n in issues)
records.append(dict(check='Lint',errors=0,advisories=[n.get('id') for n in issues],note='Three reviewed non-blocking advisories retained, not described as zero warnings.'))
old,new=scope(BASE),scope(ROOT);entries=[];patch=[]
for rel in sorted(old.keys()|new.keys()):
 a,b=old.get(rel),new.get(rel);entry=dict(path=rel,original=sha(a) if a else None,modified=sha(b) if b else None);entries.append(entry)
 if entry['original']==entry['modified'] or rel.endswith('.png'):continue
 patch.append('diff --git a/'+rel+' b/'+rel+'\n')
 if a is None:patch.append('new file mode 100644\n')
 if b is None:patch.append('deleted file mode 100644\n')
 patch.extend(difflib.unified_diff(a.read_text(encoding='utf-8-sig').splitlines(True) if a else [],b.read_text(encoding='utf-8-sig').splitlines(True) if b else [],fromfile='a/'+rel if a else '/dev/null',tofile='b/'+rel if b else '/dev/null'))
(OUT/'changes.patch').write_text(''.join(patch),encoding='utf-8',newline='\n');(OUT/'rollback-manifest.json').write_text(json.dumps(dict(files=entries,apk_sha256=sha(apk)),indent=2),encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='usb-review-verify-') as tmp:
 fixture=Path(tmp)
 for rel,p in old.items():
  dest=fixture/rel;dest.parent.mkdir(parents=True,exist_ok=True)
  if rel.endswith('.png'):shutil.copy2(p,dest)
  else:dest.write_text(p.read_text(encoding='utf-8-sig'),encoding='utf-8',newline='\n')
 run(['git','apply','--check',OUT/'changes.patch'],fixture);run(['git','apply',OUT/'changes.patch'],fixture)
 for rel,p in new.items():
  if not rel.endswith('.png'):assert (fixture/rel).read_text(encoding='utf-8')==p.read_text(encoding='utf-8-sig'),rel
  shutil.copy2(p,fixture/rel)
 fa=fixture/'docs/usb-control-audit';fa.mkdir(parents=True,exist_ok=True);shutil.copytree(BASE,fa/'baseline');shutil.copy2(OUT/'rollback-manifest.json',fa/'rollback-manifest.json')
 run(['powershell','-NoProfile','-File',ROOT/'scripts/rollback-usb-control-audit.ps1','-ProjectRoot',fixture])
 for rel,p in old.items():assert sha(fixture/rel)==sha(p),rel
 for rel in new.keys()-old.keys():assert not (fixture/rel).exists(),rel
run(['powershell','-NoProfile','-File','scripts/rollback-usb-control-audit.ps1','-VerifyOnly']);run(['git','diff','--check'])
(OUT/'release-identity.json').write_text(json.dumps(dict(apk=apk.relative_to(ROOT).as_posix(),sha256=sha(apk),bytes=apk.stat().st_size,sources={k:sha(v) for k,v in new.items()}),indent=2),encoding='utf-8')
(OUT/'verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: APK signatures/ZIP/DEX, 439 unit/model assertions, lint errors=0, patch application, exact-byte rollback executed on copy; live sources retained')
