# 目录整理记录

日期：2026-10-19（Asia/Shanghai）。

根目录的 26 个文件按用途迁入 `analysis/`、`releases/`、`scripts/`、`docs/verification/`、`logs/` 和 `original/`。根目录仅保留 README 文件及分类目录。未删除 APK、历史版本、日志或分析材料。

## 路径适配

- 安装、自动部署脚本从脚本目录相对读取 `../releases/` 中的安装包。
- 自动部署日志写入 `../logs/`。
- Python 扫描脚本默认读取 `original/fanqie_baseline_v490.apk`，支持显式 APK 参数，不再依赖调用时的工作目录。
- 根 README 的安装、回滚及产物路径已更新。
- 三个模块/源码目录和原有基线 APK 内容未变。历史记录及发布补丁保留当时的路径，不篡改旧证据。

## 证据与备份

- `manifest.json`：逐项迁移路径、原始及整理后 SHA-256。
- `backup/`：所有修改文件的原始字节副本。
- `content-changes.patch`：README 和脚本的内容差异；完整迁移映射见 manifest。
- `verification.json`：实际执行的命令、工作目录、原始输出及退出状态。
- `verify.py`：可重跑的验证；仅在临时副本执行目录回滚，不操作设备。
- `organize.py`：本次一次性整理脚本，已执行；不是日常入口，不要重复运行。

## 已完成验证

- 26 个归档文件的哈希及新路径检查通过，未修改文件字节一致。
- 全部 PowerShell 脚本 AST 解析通过；两个发布 ZIP 的 CRC 检查通过。
- 原扫描脚本与新扫描脚本在临时目录读取相同 APK，输出逐字节一致。
- 在独立副本执行目录回滚，26 个文件及 README 恢复原始哈希。
- `git diff --check` 通过；三个模块/源码目录及原有基线 APK 的 Git 差异为空。
- 没有执行 ADB 安装、重启、模块卸载等设备操作。

## 撤销本次整理

在项目根目录执行：

```powershell
.\scripts\rollback-directory-layout.ps1
```

回滚先校验全部输入和备份哈希，目标路径有冲突或文件已被编辑时停止。恢复原文件位置和内容，保留整理记录及新增工具文件，不递归删除目录。
