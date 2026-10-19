import subprocess,shlex
cmds=[
 'ls -la /data/user/0/local.usbcontrol/files/audit-density/density-*',
 'ps -A -o PID,PPID,NAME,ARGS | grep -E "watchdog|AuditInstrumentation|wm density|su -c"',
 'cat /data/user/0/local.usbcontrol/files/audit-report.json',
]
for cmd in cmds:
 p=subprocess.run(['adb','-s','3B658E00PN300000','shell','su -c '+shlex.quote(cmd)],capture_output=True,timeout=15)
 print(cmd,'exit',p.returncode);print(p.stdout.decode('utf-8','replace'));print(p.stderr.decode('utf-8','replace'))
