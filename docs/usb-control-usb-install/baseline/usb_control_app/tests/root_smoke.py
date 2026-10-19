"""Detached root-backend test; dedicated disposable AOSP emulator ONLY."""
from pathlib import Path
import subprocess, json, time

root=Path(__file__).resolve().parents[2]
out=root/'docs/usb-control-app'
adb=['adb','-s','emulator-5556']
records=[]
def call(args,required=True):
    p=subprocess.run(adb+args,capture_output=True,timeout=45)
    records.append(dict(command=subprocess.list2cmdline(adb+args),exit=p.returncode,stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace')))
    if required: assert p.returncode==0,records[-1]
    return p
deadline=time.time()+300
while time.time()<deadline:
    p=call(['shell','getprop','sys.boot_completed'],False)
    if p.returncode==0 and p.stdout.strip()==b'1':break
    time.sleep(5)
else:raise TimeoutError('Disposable AOSP emulator boot')
assert call(['shell','getprop','ro.kernel.qemu']).stdout.strip()==b'1'
assert call(['shell','getprop','ro.build.type']).stdout.strip()==b'userdebug'
base='/data/local/tmp/usb-control-test'
call(['shell','mkdir','-p',base+'/bin'])
call(['push',str(root/'usb_control_app/tests/aosp-su.sh'),base+'/bin/su'])
call(['push',str(root/'usb_control_app/build/root-dex/classes.dex'),base+'/tests.dex'])
call(['shell','chmod','755',base+'/bin/su'])
# Dedicated emulator input fixture: active developer/ADB state, no vendor install key.
runner=root/'usb_control_app/build/root-runner.sh'
runner.write_text("#!/system/bin/sh\nexport PATH="+base+"/bin:$PATH\nsu -c 'settings put global development_settings_enabled 1; settings put global adb_enabled 1; settings delete global adb_install_enabled'\ndalvikvm -cp "+base+"/tests.dex local.usbcontrol.RootBridgeTest > "+base+"/result.log 2>&1\necho $? > "+base+"/result.exit\n",encoding='utf-8',newline='\n')
call(['push',str(runner),base+'/runner.sh'])
# ADB is deliberately toggled: detach both terminal and process group first.
call(['shell','nohup setsid sh '+base+'/runner.sh </dev/null >/dev/null 2>&1 &'])
deadline=time.time()+180
while time.time()<deadline:
    time.sleep(5)
    p=call(['shell','cat',base+'/result.exit'],False)
    if p.returncode==0 and p.stdout.strip():break
else:raise TimeoutError('Detached root backend tests')
log=call(['shell','cat',base+'/result.log']).stdout.decode('utf-8','replace')
assert p.stdout.strip()==b'0',log
assert 'PASS rollback restores every original emulator setting' in log,log
records.append(dict(result='PASS',scope='AOSP root command backend and synthetic USB-install key only; not OEM installer or real-phone root-manager verification'))
(out/'root-verification.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
(out/'root-test.log').write_text(log,encoding='utf-8')
print(log)
