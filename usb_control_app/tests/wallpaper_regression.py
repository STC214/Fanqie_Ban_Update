"""Adversarial recovery cases on isolated Android directories; never use real wallpapers."""
from pathlib import Path
import subprocess, shlex, hashlib, json, argparse

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--baseline', action='store_true')
args = parser.parse_args()
OUT = ROOT / 'docs/usb-control-wallpaper/review'
OUT.mkdir(parents=True, exist_ok=True)
records = []
record_file = OUT / ('regression-baseline.json' if args.baseline else 'regression.json')


def run(command, code=0):
    p = subprocess.run(command, capture_output=True, timeout=60)
    row = dict(command=subprocess.list2cmdline(command), exit=p.returncode,
               stdout=p.stdout.decode('utf-8', 'replace'), stderr=p.stderr.decode('utf-8', 'replace'))
    records.append(row)
    record_file.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
    assert p.returncode == code, row
    return row['stdout'].strip()


def shell(command, code=0):
    return run(['adb', 'shell', 'su -c ' + shlex.quote(command)], code)


base = shell('mktemp -d /data/local/tmp/wallpaper-recovery.XXXXXX')
shell(f'chmod 777 {base}; mkdir {base}/media {base}/state; chmod 777 {base}/media')
script = ROOT / 'usb_control_app/res/raw/wallpaper.sh'
if args.baseline:
    import zipfile
    with zipfile.ZipFile(OUT / 'pre-review.zip') as z:
        script = OUT / 'baseline-wallpaper.sh'
        script.write_bytes(z.read('usb_control_app/res/raw/wallpaper.sh'))
run(['adb', 'push', str(script), base + '/wallpaper.sh'])
cmd = f'sh {base}/wallpaper.sh {base}/media {base}/state'
old = 'LocalImport_' + 'abcdef0123456789abcdef0123456789'
new = 'LocalImport_&_' + 'abcdef0123456789abcdef0123456789'
data = b'\x00\x00\x00\x18ftypisom\x00\x00\x00\x00isommp41recovery'
digest = hashlib.sha256(data).hexdigest()
fixture = OUT / 'recovery.mp4'
fixture.write_bytes(data)
run(['adb', 'push', str(fixture), base + '/media/' + old])
journal = base + '/state/wallpaper-renames.tsv'
row = digest + '\t' + old + '\t' + new + '\n'


def write_record(content):
    f = OUT / 'test-journal.tsv'
    f.write_bytes(content.encode())
    # State is private to root; copy a staged input rather than exposing it.
    run(['adb', 'push', str(f), base + '/input.tsv'])
    shell(f'cp {base}/input.tsv {journal}')


write_record('damaged')
if args.baseline:
    output = shell(cmd + ' apply')
    assert '已处理 1 个' in output
    records.append(dict(result='REPRODUCED', finding='Original code renames a file despite damaged undo journal'))
    record_file.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
    print('REPRODUCED: baseline renamed a file with a damaged journal')
    raise SystemExit(0)

# Both operations reject invalid state before changing files.
for content in ['damaged', row.rstrip('\n'), row.replace(digest, 'z' * 64),
                row.replace(old, '../outside'), row + 'incomplete', row + '\n']:
    write_record(content)
    shell(cmd + ' apply', 6)
    shell(cmd + ' undo', 6)
    assert shell('sha256sum ' + shlex.quote(base + '/media/' + old)).startswith(digest)
    shell('test ! -e ' + shlex.quote(base + '/media/' + new))

# Write-ahead interruption before mv: undo treats the original as already restored.
write_record(row)
assert '已撤销 1 个' in shell(cmd + ' undo')
# Write-ahead interruption after mv: the same record restores the original.
write_record(row)
shell('mv ' + shlex.quote(base + '/media/' + old) + ' ' + shlex.quote(base + '/media/' + new))
assert '已撤销 1 个' in shell(cmd + ' undo')
assert '没有待撤销' in shell(cmd + ' undo')
# Repeated batches, uppercase identifier and duplicate destinations remain deterministic.
upper = 'LocalImport&' + 'ABCDEF0123456789ABCDEF0123456789'
run(['adb', 'push', str(fixture), base + '/media/' + upper])
assert '已处理 2 个' in shell(cmd + ' apply')
second = 'LocalImport__' + '1' * 32
run(['adb', 'push', str(fixture), base + '/media/' + second])
assert '已处理 1 个' in shell(cmd + ' apply')
assert '已处理 0 个' in shell(cmd + ' apply')
assert '已撤销 3 个' in shell(cmd + ' undo')
assert shell('sha256sum ' + shlex.quote(base + '/media/' + upper)).startswith(digest)
# Reject a journal symlink, a state symlink, malformed arguments, and a FIFO lock.
shell(f'mv {journal} {base}/saved.tsv; ln -s {base}/saved.tsv {journal}')
shell(cmd + ' apply', 5)
shell(f'unlink {journal}; mv {base}/saved.tsv {journal}; ln -s {base}/state {base}/linked-state')
shell(f'sh {base}/wallpaper.sh {base}/media {base}/linked-state apply', 2)
shell(f'sh {base}/wallpaper.sh', 8)
shell(cmd + ' wrong', 8)
shell(f'mv {base}/state/wallpaper.lock {base}/saved.lock; mkfifo {base}/state/wallpaper.lock')
shell(cmd + ' apply', 3)
records.append(dict(result='PASS', fixture=base, checks=[
    'damaged journal blocks apply and undo before mutation', 'missing final newline rejected',
    'invalid hash and traversal rejected', 'partial trailing row and blank row rejected',
    'write-ahead crash before and after rename recoverable', 'repeat undo idempotent',
    'multiple batches accumulated and undone', 'uppercase suffix preserved',
    'journal/state symlinks rejected', 'bad argument count and mode rejected', 'FIFO lock rejected']))
record_file.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: recovery/adversarial regression; original names and SHA256 restored')
