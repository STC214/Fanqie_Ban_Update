"""Run only on the explicitly selected, user-approved phone; restore all system values."""
from pathlib import Path
import json,re,shlex,subprocess,time,argparse,uuid

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--round',default='round2');opts=parser.parse_args()
OUT=ROOT/'docs/usb-control-audit'/opts.round;OUT.mkdir(parents=True,exist_ok=True)
SERIAL='3B658E00PN300000'
ADB=['adb','-s',SERIAL]
records=[]
def adb(args,required=True,timeout=45):
    p=subprocess.run(ADB+args,capture_output=True,timeout=timeout)
    records.append(dict(command=subprocess.list2cmdline(ADB+args),exit=p.returncode,stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')))
    if required:assert p.returncode==0,records[-1]
    return p
def root(cmd,required=True):return adb(['shell','su -c '+shlex.quote(cmd)],required)
def snapshot():
    return {key:root(cmd).stdout.decode().strip() for key,cmd in {
        'density':'wm density','adb':'settings get global adb_enabled','development':'settings get global development_settings_enabled',
        'wifi':'settings get global adb_wifi_enabled','install_global':'settings get global adb_install_enabled',
        'install_secure':'settings get secure adb_install_enabled','install_system':'settings get system adb_install_enabled'}.items()}
assert root('id -u').stdout.strip()==b'0'
before=snapshot();(OUT/'phone-baseline.json').write_text(json.dumps(before,indent=2),encoding='utf-8')
override=re.search(r'Override density: (\d+)',before['density']);density=override.group(1) if override else 'reset'
assert before['adb']=='1', 'USB connection recovery requires an initially enabled ADB fixture'
assert re.fullmatch(r'null|[0-9]+',before['development'])
remote='/data/local/tmp/usb-control-audit-'+str(uuid.uuid4())
root('mkdir -p '+remote)
script=ROOT/'usb_control_app/build/phone-emergency.sh'
dev=('settings delete global development_settings_enabled' if before['development']=='null' else 'settings put global development_settings_enabled '+before['development'])
script.write_text('#!/system/bin/sh\ncd '+remote+' || exit 1\nprintf ready > armed\nsleep 300\nif mv pending expired 2>/dev/null; then\nwm density '+density+'\n'+dev+'\nsettings put global adb_enabled 1\nprintf recovered > recovery.done\nfi\n',encoding='utf-8',newline='\n')
adb(['push',str(script),'/data/local/tmp/usb-control-audit-emergency.sh'])
root('cp /data/local/tmp/usb-control-audit-emergency.sh '+remote+'/emergency.sh')
root('rm -f /data/user/0/local.usbcontrol/files/audit-report.json')
root('printf pending > '+remote+'/pending; nohup setsid sh '+remote+'/emergency.sh </dev/null >/dev/null 2>&1 & p=$!; sleep 1; kill -0 $p && test -f '+remote+'/armed')
instrumentation=None
try:
    instrumentation=subprocess.Popen(ADB+['shell','am','instrument','-w','local.usbcontrol.audit/local.usbcontrol.audit.AuditInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    deadline=time.time()+240
    while time.time()<deadline:
        time.sleep(4)
        p=root('cat /data/user/0/local.usbcontrol/files/audit-report.json',False)
        if p.returncode==0 and p.stdout.strip():
            report=json.loads(p.stdout.decode('utf-8'));break
    else:
        adb(['shell','am','force-stop','local.usbcontrol'],False)
        root('for f in /data/user/0/local.usbcontrol/files/audit-density/density-*/pending; do [ ! -f "$f" ] || mv "$f" "$f.cancelled"; done',False)
        raise TimeoutError('Phone instrumentation did not finish')
    (OUT/'phone-instrumentation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    assert report.get('passed'),report
finally:
    # Restore on host even if the test assertion failed; emergency worker remains if ADB is lost.
    restored=root('wm density '+density+' && '+dev+' && settings put global adb_enabled '+before['adb'],False)
    if restored.returncode==0:
        after=snapshot();(OUT/'phone-after.json').write_text(json.dumps(after,indent=2),encoding='utf-8')
        if before==after:root('rm -f '+remote+'/pending')
        else:records.append(dict(check='host restore comparison',before=before,after=after));raise AssertionError('Phone state differs; emergency recovery retained')
    if instrumentation is not None:
        try:
            output,error=instrumentation.communicate(timeout=5)
            records.append(dict(command='am instrument (ADB may disconnect during USB toggle)',exit=instrumentation.returncode,stdout=output.decode('utf-8','replace'),stderr=error.decode('utf-8','replace')))
        except subprocess.TimeoutExpired:instrumentation.terminate()
    (OUT/'phone-commands.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: real app-process Root, density timeout/confirm/rollback/recovery, USB OFF/ON, background persistence; phone baseline fully restored')
