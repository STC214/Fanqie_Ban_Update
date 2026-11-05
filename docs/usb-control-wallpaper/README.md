# 轻控 1.0.5：一键循环壁纸

日期：2026-11-05。设备型号 `2510DRK44C`，Android 17；已通过 Root 包安装器覆盖安装最终 1.0.5，保留应用数据。

## 已记入源码的目录与规则

目录：`/sdcard/Android/data/com.android.thememanager/files/MIUI/.videowallpaper/`。
规范名：`LocalImport_&_<32位十六进制标识>`，无扩展名，实际 MP4。
变更点：新增 `WallpaperLoop`、资源脚本 `res/raw/wallpaper.sh`，主界面「04 / 壁纸小工具」新增执行和撤销按钮。只替换分隔符，标识、权限及视频内容保留。

## 验证结果与边界

- `verification.json`：真机临时隔离目录内执行交付脚本。`_`、`&`、`__&&_`、`_&` 四种变体改名；再次执行为 0；撤销后完整文件名和 SHA256 一致。覆盖冲突、非视频、目录、符号链接、异常名、扩展名、原名占用、内容变化、缺失目录、损坏撤销日志、锁排他性。
- `release-verification.json`：实际执行 `rollback.ps1 -Mode Apk`，输出 `Success` 并读回 1.0.4；重新安装最终 APK 输出 `Success` 并读回 1.0.5。实际壁纸目录执行输出「已处理 0 个；已规范 5 个；跳过 1 个；重名冲突 0 个；失败 0 个。」；撤销输出「没有待撤销的壁纸改名。」；原 5 个视频及元数据文件名和 SHA256 不变。
- `source-rollback-verification.json`：对修改后源码的独立副本实际执行反向补丁，逐字节核对原始 ZIP 内容，新功能源码被移除。`rollback.ps1 -Mode Verify` 也已执行通过。
- `build.log`：Java、D8、资源打包及签名验证通过；最终 APK 的 SHA256 在 `.apk.sha256` 与发布验证记录中。
- 界面点击和连续播放尚未验证：本机 `uiautomator dump` 被系统终止（137）；同签名 instrumentation 启动停滞，已停止并移除测试包。`app-verification.json` 保留失败输出。不把脚本通过等同于界面或播放效果已通过。
- 普通 ADB 安装被系统返回 `INSTALL_FAILED_USER_RESTRICTED`，改用已有 Root 的包安装器成功；没有修改 USB 安装、调试、显示密度等系统设置。

## 交付与回滚

1. 修改后 APK：`../../releases/usb-control-v1.0.5.apk`。
2. 补丁：`changes.patch`；原件与哈希：`baseline-source.zip`、`baseline-sha256.json`，原 APK 保留在 ZIP 的 `releases/usb-control-v1.0.4.apk` 条目中（回滚脚本自动提取并验哈希）；旧包已移出公开 releases 目录。
3. 验证记录：`verification.json`、`release-verification.json`、`source-rollback-verification.json`（均含实际命令、输入、输出及退出状态）。
4. 可运行回滚：`rollback.ps1`，默认只校验；`-Mode Source` 恢复源码，`-Mode Apk` 保留数据回装旧版，`-Mode Wallpapers` 执行手机私有记录的累计改名撤销。壁纸撤销要求此前至少在 App 内运行过一次功能；也可直接点 App 的「撤销壁纸改名」。

重跑隔离测试：`python usb_control_app/tests/wallpaper_phone.py`。它使用新建临时目录，不触碰真实壁纸目录。
卸载 App 会清除撤销记录；请先撤销。回装旧 APK 不自动撤销壁纸文件名。源码回滚前使用 `-Mode Verify`，存在后续变更而补丁不匹配时停止，保留当前文件。

## 后续发布前重复审查

上述首轮记录保留为历史；最终包及完整审查结论见 [`review/REPORT.md`](review/REPORT.md)。已补齐轻控自身进程对真实打包资源和 Root 通道的集成验证。按用户要求，发布前不再截图或进行界面查看。
