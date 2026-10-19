# 轻控历史安装包清理

项目内仅保留 1.0.4 APK、SHA-256 与 idsig 配套文件。已移出 1.0.0–1.0.3 的发布文件及审查目录中的旧 APK 副本，共 17 个文件；源码、图片、手机状态和其他模块未改动。不重写 Git 历史。

- 当前安装包：`../../releases/usb-control-v1.0.4.apk`
- 变更清单（旧相对路径、原哈希及大小）：`manifest.json`
- 最新版校验：`verification.json`
- 回滚实测：`rollback-verification.json`
- 最新版校验命令：`python scripts/verify-usb-control-latest.py`
- 回滚命令：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restore-usb-control-history.ps1`

旧文件已按原相对目录归档在项目外，确切本机位置记录于 `.local/release-cleanup/manifest.json`。恢复命令先验证全部归档哈希并防止覆盖不同的现有文件，再复制恢复；已在项目外临时副本真实执行、逐文件核对，项目内仍保持仅最新版。

历史审查源码与报告保留原样，但原有核对旧 APK 的脚本依赖已归档的二进制，仅在还原后复验历史结论。当前验证入口独立于历史 APK，已通过签名、对齐、版本、SHA-256、ZIP 完整性、34 项逻辑、420 项事务和 23 项适配测试。
