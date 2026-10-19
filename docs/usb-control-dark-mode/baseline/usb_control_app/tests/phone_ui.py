"""Foreground UI regression on the approved phone; preserves the original density."""
from pathlib import Path
import subprocess,shlex,json,re,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/usb-control-audit/phone-ui';OUT.mkdir(exist_ok=True)
ADB=['adb','-s','3B658E00PN300000'];records=[]
def run(args,binary=False):
 p=subprocess.run(ADB+args,capture_output=True,timeout=45);records.append(dict(command=subprocess.list2cmdline(ADB+args),exit=p.returncode,stdout='<PNG>' if binary else p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')));assert p.returncode==0,records[-1];return p.stdout
def root(cmd):return run(['shell','su -c '+shlex.quote(cmd)]).decode().strip()
def dump(name):
 run(['shell','uiautomator','dump','/sdcard/usb-control-review.xml']);data=run(['exec-out','cat','/sdcard/usb-control-review.xml']);(OUT/name).write_bytes(data);return ET.fromstring(data)
def find(tree,**attrs):return next(n for n in tree.iter('node') if all(n.get(k)==v for k,v in attrs.items()))
def tap(n):
 x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')));run(['shell','input','tap',str((x+X)//2),str((y+Y)//2)])
def enter(tree,value):
 tap(find(tree,**{'class':'android.widget.EditText'}));run(['shell','input','keyevent','123','67','67','67','67','67']);run(['shell','input','text',value])
before=root('wm density');m=re.search(r'Override density: (\d+)',before);prior=m.group(1) if m else 'reset'
root('rm -f /data/user/0/local.usbcontrol/files/ui-audit-ready /data/user/0/local.usbcontrol/files/ui-audit-done')
hold=subprocess.Popen(ADB+['shell','am','instrument','-w','-e','mode','ui','local.usbcontrol.audit/local.usbcontrol.audit.AuditInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
for _ in range(30):
 p=subprocess.run(ADB+['shell','su -c '+shlex.quote('test -f /data/user/0/local.usbcontrol/files/ui-audit-ready')],capture_output=True)
 if p.returncode==0:break
 time.sleep(1)
else:raise TimeoutError('Foreground UI harness')
try:
 run(['shell','am','start','-n','local.usbcontrol/.MainActivity']);time.sleep(3);tree=dump('main.xml')
 switches=[n for n in tree.iter('node') if n.get('class')=='android.widget.Switch'];assert len(switches)==2 and switches[0].get('enabled')=='true' and switches[0].get('checked')=='true' and switches[1].get('enabled')=='false'
 (OUT/'main.png').write_bytes(run(['exec-out','screencap','-p'],True))
 tap(switches[0]);tree=dump('usb-confirm.xml');tap(find(tree,text='取消'));assert root('settings get global adb_enabled')=='1'
 tree=dump('main-after-cancel.xml');tap(find(tree,text='调整最小宽度'));tree=dump('density-input.xml');enter(tree,'0');tree=dump('invalid-entered.xml');tap(find(tree,text='预览应用'));tree=dump('invalid-input.xml')
 assert any(n.get('text')=='0' and n.get('class')=='android.widget.EditText' for n in tree.iter('node'));assert root('wm density')==before
 tap(find(tree,text='取消'));tree=dump('before-preview.xml');tap(find(tree,text='调整最小宽度'));tree=dump('valid-input.xml');enter(tree,'410');tree=dump('valid-entered.xml');tap(find(tree,text='预览应用'));time.sleep(3)
 tree=dump('preview-confirm.xml');assert any(n.get('text')=='保留这个显示大小？' for n in tree.iter('node'))
 (OUT/'density-preview.png').write_bytes(run(['exec-out','screencap','-p'],True));assert root('wm density')!=before
 time.sleep(22);assert root('wm density')==before
 tree=dump('after-timeout.xml');tap(find(tree,text='调整最小宽度'));tree=dump('confirm-input.xml');enter(tree,'410');tree=dump('confirm-entered.xml');tap(find(tree,text='预览应用'));time.sleep(2)
 tree=dump('save-confirm.xml');tap(find(tree,text='保留'));time.sleep(2);assert root('wm density')!=before
 run(['shell','input','swipe','600','2350','600','800','450']);tree=dump('restore-menu.xml');tap(find(tree,text='恢复首次使用前的系统设置'));tree=dump('restore-confirm.xml');tap(find(tree,text='恢复'));time.sleep(3);assert root('wm density')==before
 records.append(dict(result='PASS',checks=['real phone supported/unsupported switch states','USB cancel does not change setting','invalid minimum width rejected','DPI preview UI and automatic rollback','DPI confirmation commits','in-app initial-system restore readback']))
finally:
 root('wm density '+prior)
 root('touch /data/user/0/local.usbcontrol/files/ui-audit-done')
 try:hold.communicate(timeout=10)
 except subprocess.TimeoutExpired:hold.terminate()
 (OUT/'commands.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: real phone UI, USB cancellation, invalid DPI, countdown rollback, confirm and in-app restoration')
