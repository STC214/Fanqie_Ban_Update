"""Dark/light/system UI regression. Run against disposable emulator only."""
from pathlib import Path
import subprocess,json,time,re,xml.etree.ElementTree as ET,io
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/usb-control-dark-mode/theme-ui';OUT.mkdir(exist_ok=True,parents=True)
ADB=['adb','-s','emulator-5554'];records=[]
def run(args,binary=False):
 p=subprocess.run(ADB+args,capture_output=True,timeout=45)
 records.append(dict(command=subprocess.list2cmdline(ADB+args),exit=p.returncode,stdout='<PNG>' if binary else p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')))
 assert p.returncode==0,records[-1]
 return p.stdout

def dump(name):
 run(['shell','uiautomator','dump','/sdcard/dark-test.xml'])
 data=run(['exec-out','cat','/sdcard/dark-test.xml']);(OUT/(name+'.xml')).write_bytes(data);return ET.fromstring(data)
def find(t,text):return next(n for n in t.iter('node') if n.get('text')==text)
def tap(n):
 x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')));run(['shell','input','tap',str((x+X)//2),str((y+Y)//2)])
def shot(name,dark=None):
 data=run(['exec-out','screencap','-p'],True);(OUT/(name+'.png')).write_bytes(data)
 if dark is not None:
  colors=Image.open(io.BytesIO(data)).convert('RGB').get_flattened_data();expected=(230,237,248) if dark else (25,43,72)
  count=sum(c==expected for c in colors);assert count>80,(name,expected,count)
def restart():
 run(['shell','am','force-stop','local.usbcontrol']);run(['shell','am','start','-n','local.usbcontrol/.MainActivity']);time.sleep(4)
def appearance():
 for i in range(5):
  t=dump('find-appearance')
  mode=next((n for n in t.iter('node') if n.get('text','').startswith('外观模式 · ')),None)
  if mode is not None:return t,mode
  run(['shell','input','swipe','520','2000','520','700','300'])
 raise AssertionError('Appearance control missing')
def reveal(text):
 for i in range(5):
  t=dump('reveal');n=next((n for n in t.iter('node') if n.get('text')==text),None)
  if n is not None:return n
  run(['shell','input','swipe','520','2000','520','700','300'])
 raise AssertionError('Control not visible: '+text)
def choose(label):
 t,b=appearance();tap(b);t=dump('chooser-'+label);shot('chooser-'+label);tap(find(t,label));time.sleep(.5)
original=run(['shell','cmd','uimode','night']).decode().strip();assert any(x in original for x in ['yes','no','auto']),original
try:
 run(['shell','logcat','-c']);run(['shell','cmd','uimode','night','no']);restart()
 choose('暗色');shot('dark-main',True)
 restart();shot('dark-persisted',True)
 # Explicit dark remains dark even if system light; native dialog and actual crop preview.
 t,b=appearance();assert '暗色' in b.get('text');tap(reveal('取景与透明度'))
 t=dump('dark-editor');assert any(n.get('class')=='android.widget.SeekBar' for n in t.iter('node'));shot('dark-editor',True)
 opacity=next(n.get('text') for n in t.iter('node') if n.get('text','').startswith('图片透明度 '));tap(find(t,'取消'))
 choose('浅色');shot('light-main',False)
 run(['shell','cmd','uimode','night','yes']);time.sleep(4);shot('light-overrides-system-dark',False)
 choose('跟随系统');shot('system-dark',True)
 run(['shell','cmd','uimode','night','no']);time.sleep(4);shot('system-live-light',False)
 run(['shell','cmd','uimode','night','yes']);time.sleep(4);shot('system-live-dark',True)
 restart();shot('system-dark-restarted',True)
 t,b=appearance();assert '跟随系统' in b.get('text');tap(reveal('取景与透明度'));t=dump('preserved-background');assert any(n.get('text')==opacity for n in t.iter('node'));tap(find(t,'取消'))
 assert b'FATAL EXCEPTION' not in run(['shell','logcat','-d','-s','AndroidRuntime:E'])
 records.append(dict(result='PASS',checks=['dark palette pixels','light palette pixels','manual dark persists','manual light overrides system dark','follow system changes live both directions','restart persistence','dark native dialog/editor/slider','existing background opacity preserved','no runtime crash']))
 print('PASS: dark/light/system, native editor, image persistence and restart')
finally:
 setting=next(x for x in ['yes','no','auto'] if x in original);run(['shell','cmd','uimode','night',setting])
 (OUT/'verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')


