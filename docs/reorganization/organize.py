from pathlib import Path
import shutil, hashlib, json, difflib

root = Path(__file__).resolve().parents[2]
audit = root / 'docs/reorganization'
groups = {
    'releases': ['*.zip', '*.patch'],
    'scripts': ['*.ps1', '*.py'],
    'analysis': ['*.smali.txt', 'AndroidManifest.decoded.xml', 'game_*.txt', 'minigame_c_methods.txt', 'update_string_hits.txt', 'video_*.txt'],
    'docs/verification': ['baseline.txt', 'original_apk.sha256', 'verification_record*.md'],
    'logs': ['*.log'],
    'original': ['*.apk'],
}
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
rows = []
for folder, patterns in groups.items():
    for pattern in patterns:
        for src in sorted(root.glob(pattern)):
            dst = root / folder / src.name
            if dst.exists():
                raise RuntimeError(f'Destination exists: {dst}')
            rows.append(dict(source=src.name, destination=f'{folder}/{src.name}', sha256=sha(src)))
changed = ['README.md', 'auto-deploy-v2.1.ps1', '_auto_usb_v130.ps1', 'install-v2.1.ps1', 'install-usb-debug-toggle.ps1', 'scan_update_strings.py']
(audit / 'backup').mkdir(exist_ok=True)
for name in changed:
    shutil.copy2(root / name, audit / 'backup' / name)
for row in rows:
    dst = root / row['destination']
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(root / row['source']), str(dst))
for name in ['install-v2.1.ps1', 'install-usb-debug-toggle.ps1']:
    p = root / 'scripts' / name
    s = p.read_text(encoding='utf-8-sig')
    s = s.replace('Join-Path $PSScriptRoot "', 'Join-Path $PSScriptRoot "../releases/').replace("Join-Path $PSScriptRoot '", "Join-Path $PSScriptRoot '../releases/")
    p.write_text(s, encoding='utf-8-sig')
for name in ['auto-deploy-v2.1.ps1', '_auto_usb_v130.ps1']:
    p = root / 'scripts' / name
    s = p.read_text(encoding='utf-8-sig')
    for quote in ['"', "'"]:
        for prefix in ['fanqie_update_video_game_blocker', 'usb_debug_toggle']:
            s = s.replace(f'Join-Path $root {quote}{prefix}', f'Join-Path $root {quote}../releases/{prefix}')
        for log in ['auto-deploy-v2.1.3.log', '_usb_v130_deploy.log']:
            s = s.replace(f'Join-Path $root {quote}{log}', f'Join-Path $root {quote}../logs/{log}')
    p.write_text(s, encoding='utf-8-sig')
p = root / 'scripts/scan_update_strings.py'
s = p.read_text(encoding='utf-8-sig').replace('import zipfile,re,os,collections', 'import zipfile,re\nfrom pathlib import Path\nimport argparse')
s = s.replace("apk=next(x for x in os.listdir('.') if x.lower().endswith('.apk'))", "parser=argparse.ArgumentParser(description='Scan APK update-related strings')\nparser.add_argument('apk', nargs='?', type=Path, default=Path(__file__).resolve().parents[1] / 'original' / 'fanqie_baseline_v490.apk')\napk=parser.parse_args().apk")
p.write_text(s, encoding='utf-8')
p = root / 'README.md'
s = p.read_text(encoding='utf-8-sig')
s = s.replace('`番茄小说oppo定制.apk`', '`original/番茄小说oppo定制.apk`')
s = s.replace('.\\install-v2.1.ps1', '.\\scripts\\install-v2.1.ps1').replace('.\\rollback-v2.ps1', '.\\scripts\\rollback-v2.ps1')
s = s.replace('adb push .\\fanqie_update_video_game_blocker', 'adb push .\\releases\\fanqie_update_video_game_blocker')
s = s.replace('在 SukiSU/KernelSU 管理器中选择：', '在 SukiSU/KernelSU 管理器中选择 `releases/` 下的安装包：')
start = s.index('项目目录中的主要产物：')
end = s.index('## 审查结果', start)
s = s[:start] + '''项目目录按用途分类（源码目录保持原位）：

```text
README.md                       # 项目说明和使用入口
fanqie_update_blocker/           # 主模块
usb_debug_toggle/               # USB 调试切换模块
xposed_hook/                    # Hook 源码、构建产物及依赖
original/                      # 原始 APK 与基线副本，均保留
releases/                      # 发布 ZIP 与对应补丁
scripts/                       # 安装、部署、设备回滚、分析脚本
analysis/                      # 清单、Smali 导出、字符串及域名分析
docs/verification/             # 历史基线、哈希及真机验证记录
docs/reorganization/           # 整理清单、备份、差异及验证
logs/                          # 部署日志
```

- 安装包：`releases/fanqie_update_video_game_blocker-v2.1.3.zip`
- USB 模块安装：`.\\scripts\\install-usb-debug-toggle.ps1`
- USB 模块回滚：`.\\scripts\\rollback-usb-debug-toggle.ps1`
- 分析扫描：`python .\\scripts\\scan_update_strings.py`，也支持传入 APK 路径；默认读取 `original/fanqie_baseline_v490.apk`，与当前工作目录无关。
- 历史分析输出、旧补丁和真机记录原样保留，其中旧路径表示当时的执行位置。
- 撤销本次整理：`.\\scripts\\rollback-directory-layout.ps1`，仅处理项目文件，不执行设备操作。整理后若文件被编辑，先停止以保护新内容。

''' + s[end:]
p.write_text(s, encoding='utf-8')
for row in rows:
    row['organized_sha256'] = sha(root / row['destination'])
meta = dict(moves=rows, backups=changed, readme_sha256=sha(p))
(audit / 'manifest.json').write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding='utf-8')
diff = []
for name in changed:
    dst = next((r['destination'] for r in rows if r['source'] == name), name)
    diff.extend(difflib.unified_diff((audit / 'backup' / name).read_text(encoding='utf-8-sig').splitlines(True), (root / dst).read_text(encoding='utf-8-sig').splitlines(True), fromfile='a/' + name, tofile='b/' + dst))
(audit / 'content-changes.patch').write_text(''.join(diff), encoding='utf-8')
print(f'Moved {len(rows)} files; preserved backups and hashes; updated script and README paths.')
