from pathlib import Path
import re,shlex,subprocess
text=Path('usb_control_app/build/phone-emergency.sh').read_text(encoding='utf-8')
current=re.search(r'^cd (\S+) \|\|',text,re.M).group(1)
cmd='for d in /data/local/tmp/usb-control-audit /data/local/tmp/usb-control-audit-*; do [ "$d" = '+shlex.quote(current)+' ] && continue; [ ! -d "$d" ] || rm -f "$d/pending"; done'
p=subprocess.run(['adb','-s','3B658E00PN300000','shell','su -c '+shlex.quote(cmd)],capture_output=True)
print('Preserved current emergency worker:',current,'; cancelled older audit tokens; exit=',p.returncode)
assert p.returncode==0,p.stderr
