# 轻控 App 交付记录

日期：2026-10-19（Asia/Shanghai）。

## 交付物

- 安装包：`../../releases/usb-control-v1.0.0.apk`，979,510 字节（约 957 KiB）。
- SHA-256：`92F32FB2318BC935A4A473671B8AC8655248D91B8F08B91C3D66B3ABF7708F32`。
- 源码：`../../usb_control_app/`。
- 文本源补丁：`source.patch`；图标原文件及资源副本另由 `artifacts.json` 记录相同 SHA-256。
- 基线：`baseline/README.md` 保留本轮修改前字节；原 USB 模块、其他模块及 Hook 源码保持未变。
- 卸载辅助：`../../scripts/rollback-usb-control-app.ps1`。

## 已通过

1. APK v2/v3 签名、ZIP CRC、4 字节对齐、包名和 minSdk 检查。包内不申请网络或全量相册权限。
2. 19 项 JVM 断言：USB 安装项存在/缺失/歧义检测、dp 与 dpi 换算、边界检查、等比缩放及选区边界。
3. 最终签名 APK 在 API 36.1 非 Root 模拟器安装启动，系统控制禁用，背景功能独立可用。
4. 系统文件选择器读取测试图片；拖动取景、滑块修改透明度、真实界面叠加预览、保存及强制停止后恢复。未发现 AndroidRuntime 崩溃。
5. 源码补丁在临时基线副本通过 `git apply --check` 和实际应用，逐项内容一致。
6. PowerShell 卸载脚本实际执行 `-UninstallOnly -Serial emulator-5554`，输出 `Success`，退出 0；随后查询包列表为空。测试模拟器已关闭。

上述逐项命令、原始输出与退出状态分别见 `artifact-verification.json`、`ui-verification.json`。图标与源文件清单见 `artifacts.json`。

## Root 后端验证边界

独立 AOSP API 35 userdebug 模拟器中的 Root 命令测试已确认：UID 0、USB 调试关闭/开启的设置读回、AOSP 缺失独立 USB 安装项、合成已有安装键的开/关读回，以及密度从 420 改为 480 后由独立 20 秒看门狗恢复到 `Physical density: 420`。

第一次命令测试最终因恢复 USB/开发者状态导致 ADB 测试连接断开，进程退出 1，未得到「全部初始值恢复」的最终断言；整套 Root 测试仍待验证，只记为上述子项通过。随后尝试独立后台执行的复测未获得完整结果，已停止并关闭专用模拟器。手机 App 进程中的 Root 管理器授权及完整回滚仍需实机验证。

`tests/aosp-su.sh` 只转换专用模拟器的 AOSP su 参数格式，不包含在 APK。合成 `adb_install_enabled` 键验证的是检测/读写逻辑，不代表任何厂商的真实安装策略生效。尚未在用户真机执行系统变更测试，厂商 USB 安装和 Root 管理器兼容性留待实机验收。

## 界面

- `main.png`：主界面。
- `background-preview.png`：等比取景和透明度滑块实时预览。
- `background-applied.png`：保存并重启 App 后的背景。

## 回滚使用

App 中先点击「恢复首次使用前的系统设置」，再卸载。Root 初次成功读取后，会在 App 私有目录生成本机 `restore-system.sh`，电脑辅助脚本默认先运行它再卸载；`-UninstallOnly` 仅用于明确跳过系统恢复的情况。此次只实测了辅助脚本的卸载分支，未在用户真机执行。
