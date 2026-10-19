"""Direct USB-install smoke test for the observed 1080x2400 / 420-dpi phone layout."""
from pathlib import Path
import subprocess,shlex,time,json,argparse,struct
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
out=Path('.local/fast-toggle');out.mkdir(parents=True,exist_ok=True);adb=['adb','-s',a.serial];records=[]
def run(args,binary=False):
 r=subprocess.run(adb+args,capture_output=True,timeout=30);records.append(dict(command=subprocess.list2cmdline(adb+args),exit=r.returncode,stdout='<PNG>' if binary else r.stdout.decode('utf-8','replace'),stderr=r.stderr.decode('utf-8','replace')));(out/'commands.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8');assert r.returncode==0,records[-1];return r.stdout
get='content call --uri content://com.miui.securitycenter.remoteprovider --method callPreference --arg GET --extra type:i:1 --extra key:s:security_adb_install_enable --extra default:b:false'
def read():
 value=run(['shell','su -c '+shlex.quote(get)]).decode();assert 'enable=true' in value or 'enable=false' in value;return 'enable=true' in value
before=read();density=run(['shell','wm','density']);assert b'Override density: 420' in density
adb_before=run(['shell','settings','get','global','adb_enabled']);wifi_before=run(['shell','settings','get','global','adb_wifi_enabled'])
run(['shell','am','start','-n','local.usbcontrol/.MainActivity']);time.sleep(4)
png=run(['exec-out','screencap','-p'],True);assert struct.unpack_from('>II',png,16)==(1080,2400),'Layout differs: do not use fixed touch coordinates'
for expected in [not before,before]:
 start=time.perf_counter();run(['shell','su -c '+shlex.quote('input tap 930 1083')]);deadline=time.monotonic()+50
 while time.monotonic()<deadline:
  if read()==expected:break
  time.sleep(.2)
 else:raise AssertionError('Value unchanged: check the official system flow and restore the original state in App')
 seconds=round(time.perf_counter()-start,3);records.append(dict(action='on' if expected else 'off',observed_actual_value=expected,host_observed_seconds=seconds));print('PASS:',records[-1],flush=True)
 time.sleep(2);(out/('on.png' if expected else 'off.png')).write_bytes(run(['exec-out','screencap','-p'],True))
 run(['shell','am','start','-n','local.usbcontrol/.MainActivity']);time.sleep(3)
assert read()==before
assert run(['shell','wm','density'])==density;assert run(['shell','settings','get','global','adb_enabled'])==adb_before;assert run(['shell','settings','get','global','adb_wifi_enabled'])==wifi_before
records.append(dict(result='PASS',checks=['single tap both directions without App confirmation','actual provider readback','USB-install original value restored','ADB, wireless and density unchanged']))
(out/'commands.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS: direct switches and original state restored')
