from pathlib import Path
import subprocess,shlex,json,hashlib,argparse
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--out',default='docs/usb-control-wallpaper');args=parser.parse_args()
OUT=ROOT/args.out;OUT.mkdir(parents=True,exist_ok=True);rec=[]
def run(args,expect=0):
 r=subprocess.run(args,capture_output=True,timeout=60); o=r.stdout.decode('utf-8','replace'); e=r.stderr.decode('utf-8','replace'); rec.append(dict(command=subprocess.list2cmdline(args),stdout=o,stderr=e,exit=r.returncode)); (OUT/'verification.json').write_text(json.dumps(rec,ensure_ascii=False,indent=2),encoding='utf-8'); assert r.returncode==expect,rec[-1]; return o.strip()
def shell(s,expect=0):return run(['adb','shell','su -c '+shlex.quote(s)],expect)
base=shell('mktemp -d /data/local/tmp/wallpaper-test.XXXXXX'); d=base+'/media'; st=base+'/state'; script=base+'/wallpaper.sh'
shell('chmod 777 '+base);run(['adb','push',str(ROOT/'usb_control_app/res/raw/wallpaper.sh'),script]);shell(f'mkdir {d} {st}; chmod 777 {d}')
ids=[f'{i:032x}' for i in range(1,18)]
names=['LocalImport_'+ids[0],'LocalImport&'+ids[1],'LocalImport__&&_'+ids[2],'LocalImport_&'+ids[3],'LocalImport_&_'+ids[4], 'LocalImport_'+ids[5],'LocalImport_&_'+ids[5], 'LocalImport_'+ids[6]+'.mp4','LocalImport_&_', 'LocalImport_bad', 'LocalImport_'+ids[7], 'other']
for name in names:
 p=OUT/'fixture.mp4';p.write_bytes(b'\x00\x00\x00\x18ftypisom\x00\x00\x00\x00isommp41'+name.encode());run(['adb','push',str(p),d+'/'+name])
shell('mkdir '+shlex.quote(d+'/LocalImport_'+ids[8]));shell('ln -s /system/build.prop '+shlex.quote(d+'/LocalImport_'+ids[9]));shell('printf nonvideo > '+shlex.quote(d+'/LocalImport_'+ids[7]))
cmd='sh '+shlex.quote(script)+' '+shlex.quote(d)+' '+shlex.quote(st)
before=shell('cd '+d+'; sha256sum * 2>/dev/null || true')
apply=shell(cmd+' apply'); assert '已处理 4 个' in apply and '重名冲突 1 个' in apply,apply
for i in range(4):assert shell('test -f '+shlex.quote(d+'/LocalImport_&_'+ids[i])+'; echo $?')=='0'
again=shell(cmd+' apply');assert '已处理 0 个' in again
undo=shell(cmd+' undo');assert '已撤销 4 个' in undo
assert before==shell('cd '+d+'; sha256sum * 2>/dev/null || true'),'Full original names and contents restored'
assert '没有待撤销' in shell(cmd+' undo')
# Modified content: preserve it, keep pending record, never overwrite.
shell(cmd+' apply'); altered=d+'/LocalImport_&_'+ids[0]; original=shell('cat '+shlex.quote(altered));shell('printf changed >> '+shlex.quote(altered));result=shell(cmd+' undo');assert '已撤销 3 个' in result and '内容变化 1 个' in result,result
# Restore fixture only, then consume pending journal.
p=OUT/'fixture.mp4';p.write_bytes(b'\x00\x00\x00\x18ftypisom\x00\x00\x00\x00isommp41'+names[0].encode());run(['adb','push',str(p),altered]);assert '已撤销 1 个' in shell(cmd+' undo')
# Existing original blocks undo without overwriting either file.
shell(cmd+' apply');shell('printf keep > '+shlex.quote(d+'/'+names[0]));assert '内容变化 1 个' in shell(cmd+' undo');assert shell('cat '+shlex.quote(d+'/'+names[0]))=='keep'
# Missing directory and malformed journal stop without modifying unrelated files.
shell('sh '+shlex.quote(script)+' '+shlex.quote(base+'/missing')+' '+shlex.quote(st)+' apply',1)
shell('printf bad > '+shlex.quote(st+'/wallpaper-renames.tsv'));shell(cmd+' undo',6)
# Kernel lock: a competing invocation is rejected, no stale lock after exit.
assert shell('exec 9> '+shlex.quote(st+'/wallpaper.lock')+'; flock -n 9 9>&9; '+cmd+' apply; test $? -eq 3')
rec.append(dict(result='PASS',fixture=base,checks=['four delimiter variants','canonical idempotence','target collision never overwritten','nonvideo invalid names extensions directories and symlink skipped','SHA256 exact rollback','changed-content undo blocked','original-name undo collision blocked','missing directory','journal validation','kernel lock exclusion']))
(OUT/'verification.json').write_text(json.dumps(rec,ensure_ascii=False,indent=2),encoding='utf-8');print('PASS fixture:',base)
