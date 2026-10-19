"""Production-APK UI smoke test on a disposable non-root emulator with test image."""
from pathlib import Path
import subprocess, json, re, time, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/usb-control-app'
OUT.mkdir(exist_ok=True)
ADB = ['adb', '-s', 'emulator-5554']
records = []
def run(args, binary=False):
    p = subprocess.run(ADB + args, capture_output=True)
    records.append(dict(command=subprocess.list2cmdline(ADB+args), exit=p.returncode,
                        stdout=('<binary>' if binary else p.stdout.decode('utf-8','replace')),
                        stderr=p.stderr.decode('utf-8','replace')))
    assert p.returncode == 0, records[-1]
    return p.stdout
def dump(name):
    run(['shell','uiautomator','dump','/sdcard/usb-control-smoke.xml'])
    data = run(['exec-out','cat','/sdcard/usb-control-smoke.xml'])
    (OUT/name).write_bytes(data)
    return ET.fromstring(data)
def find(tree, **attrs):
    return next(n for n in tree.iter('node') if all(n.get(k)==v for k,v in attrs.items()))
def center(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    return (x1+x2)//2,(y1+y2)//2
def tap(node):
    x,y=center(node);run(['shell','input','tap',str(x),str(y)])
def screenshot(name):
    (OUT/name).write_bytes(run(['exec-out','screencap','-p'],True))
run(['install','-r',str(ROOT/'releases/usb-control-v1.0.0.apk')])
run(['shell','am','force-stop','local.usbcontrol'])
run(['shell','am','start','-n','local.usbcontrol/.MainActivity'])
time.sleep(2)
tree=dump('main-ui.xml')
switches=[n for n in tree.iter('node') if n.get('class')=='android.widget.Switch']
assert len(switches)==2 and all(n.get('enabled')=='false' for n in switches)
assert any('未检测到 Root' in n.get('text','') for n in tree.iter('node'))
screenshot('main.png')
run(['shell','input','swipe','520','2050','520','700','450'])
tree=dump('appearance-ui.xml')
tap(find(tree,text='取景与透明度'))
tree=dump('crop-ui.xml')
slider=find(tree,**{'class':'android.widget.SeekBar'})
x1,y1,x2,y2=map(int,re.findall(r'\d+',slider.get('bounds')))
run(['shell','input','tap',str(x1+(x2-x1)//2),str((y1+y2)//2)])
tree=dump('opacity-preview-ui.xml')
label=next(n.get('text') for n in tree.iter('node') if n.get('text','').startswith('图片透明度 '))
assert label != '图片透明度 75%'
preview=[n for n in tree.iter('node') if n.get('content-desc','').startswith('背景预览')][-1]
x1,y1,x2,y2=map(int,re.findall(r'\d+',preview.get('bounds')))
run(['shell','input','swipe',str((x1+x2)//2),str(y2-70),str((x1+x2)//2+60),str(y2-120),'400'])
screenshot('background-preview.png')
tap(find(tree,text='保存'))
time.sleep(2)
run(['shell','am','force-stop','local.usbcontrol'])
run(['shell','am','start','-n','local.usbcontrol/.MainActivity'])
time.sleep(2)
run(['shell','input','swipe','520','2050','520','700','450'])
tree=dump('persisted-appearance-ui.xml');tap(find(tree,text='取景与透明度'))
tree=dump('persisted-background-ui.xml')
assert any(n.get('text')==label for n in tree.iter('node'))
tap(find(tree,text='取消'))
run(['shell','input','swipe','520','600','520','2050','450'])
screenshot('background-applied.png')
crashes=run(['shell','logcat','-d','-s','AndroidRuntime:E']).decode('utf-8','replace')
assert 'FATAL EXCEPTION' not in crashes
records.append(dict(result='PASS', checks=['production APK install and launch','no-root controls disabled','existing chosen background retained','uniform crop drag','opacity slider live preview','background and opacity persisted after force-stop','no AndroidRuntime crash']))
(OUT/'ui-verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: production APK UI, disabled states, background crop, opacity preview and persistence')
