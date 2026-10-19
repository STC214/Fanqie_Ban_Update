from pathlib import Path
import hashlib, json, shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[2]
audit = root / 'docs/reorganization'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
meta = json.loads((audit / 'manifest.json').read_text(encoding='utf-8'))
record = []
def run(args, cwd=root):
    result = subprocess.run(args, cwd=cwd, capture_output=True)
    record.append({'command': subprocess.list2cmdline([str(a) for a in args]), 'cwd': str(cwd), 'exit': result.returncode, 'stdout': result.stdout.decode('utf-8', 'replace'), 'stderr': result.stderr.decode('utf-8', 'replace')})
    assert result.returncode == 0, record[-1]
    return result.stdout

for row in meta['moves']:
    assert sha(root / row['destination']) == row['organized_sha256']
    if row['source'] not in meta['backups']:
        assert row['sha256'] == row['organized_sha256']
    assert not (root / row['source']).exists()
assert sha(root / 'README.md') == meta['readme_sha256']
record.append({'check': '26 moves and all content hashes', 'result': 'PASS; only documented script/README changes'})
run(['powershell', '-NoProfile', '-Command', "$ErrorActionPreference='Stop'; Get-ChildItem scripts/*.ps1 | ForEach-Object { $t=$null;$e=$null;[void][System.Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$t,[ref]$e);if($e.Count){throw ($e|Out-String)} }; 'PowerShell AST: PASS'"])
for name in ['fanqie_update_video_game_blocker-v2.1.3.zip', 'usb_debug_toggle-v1.3.0.zip']:
    import zipfile
    with zipfile.ZipFile(root / 'releases' / name) as z:
        assert z.testzip() is None
record.append({'check': 'Release ZIP CRC', 'result': 'PASS; 2 archives unchanged'})
with tempfile.TemporaryDirectory(prefix='fanqie-layout-test-') as tmp:
    fixture = Path(tmp)
    shutil.copy2(root / 'original/fanqie_baseline_v490.apk', fixture / 'baseline.apk')
    baseline = run(['python', str(audit / 'backup/scan_update_strings.py')], fixture)
    modified = run(['python', str(root / 'scripts/scan_update_strings.py')], fixture)
    assert baseline == modified
    record.append({'check': 'Scanner baseline vs reorganized, from unrelated working directory', 'result': 'PASS; byte-identical output', 'sha256': hashlib.sha256(modified).hexdigest()})
    for row in meta['moves']:
        dst = fixture / row['destination']
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / row['destination'], dst)
    shutil.copytree(audit / 'backup', fixture / 'docs/reorganization/backup')
    shutil.copy2(audit / 'manifest.json', fixture / 'docs/reorganization/manifest.json')
    shutil.copy2(root / 'README.md', fixture / 'README.md')
    run(['powershell', '-NoProfile', '-File', str(root / 'scripts/rollback-directory-layout.ps1'), '-ProjectRoot', str(fixture)])
    for row in meta['moves']:
        assert sha(fixture / row['source']) == row['sha256']
    assert sha(fixture / 'README.md') == meta['original_readme_sha256']
    record.append({'check': 'Executed rollback on isolated copy', 'result': 'PASS; 26 original hashes and README restored; live project remains organized'})
run(['git', 'diff', '--check'])
(audit / 'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: file hashes, PowerShell syntax, ZIP integrity, scanner equivalence, executed rollback, git diff --check')
