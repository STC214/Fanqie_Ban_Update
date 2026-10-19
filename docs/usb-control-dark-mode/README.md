# 轻控 1.0.2：暗色模式

## 交付与入口

已覆盖安装到已连接的 OnePlus，版本 1.0.2 / 102；手机已安装 APK 的 SHA-256 与本地交付完全一致（`phone-install.json`）。

入口：**03 / 你的风格 → 外观模式**。

新增持久化字段 `appearance_mode`：`system`（默认跟随系统）、`light`、`dark`。配色覆盖页面、卡片、文字、按钮、开关、状态/导航栏、原生弹窗、输入控件和图片透明度编辑预览。系统主题变化即时生效；正在显示的弹窗保持其打开时主题，关闭后应用待处理主题，避免打断显示密度确认及后台保存。图片不反色，原比例缩放、选区及透明度不受外观切换影响。

最终 APK：`../../releases/usb-control-v1.0.2.apk`

SHA-256：`826FCD2CB172A640B520F15067EB978AA0DC1B5BF42F329D00AF00E84213A41C`

## 已验证

- 新版 Android 16 模拟器 UI：暗色/浅色真实像素、手动覆盖系统、跟随系统双向实时变化、强制停止后记忆、暗色原生弹窗与背景编辑器、原背景与透明度保留、无运行崩溃。见 `theme-ui/verification.json` 和 PNG/XML。
- 页面背景缩放、旋转适配、透明度滑块预览和保存重启回归：`ui/ui-verification.json`。
- 1.0.1 基线与 1.0.2 修改版分别执行：19 项逻辑断言、420 项事务断言（20 轮），均通过。精确命令、输出、退出状态见 `verification.json`。
- 新旧 APK 签名及对齐通过，原图标资源与旧 APK 一致；旧交付包、封存基线哈希均保持一致。
- lint：0 错误，保留此前 3 条提示：平台 EXIF（维持零依赖）、target36 非最新37、基础 adaptive icon 未声明 monochrome（v33 资源已声明）。不将此结果描述为全平台零问题。
- 补丁在临时基线副本中真实应用后逐文件比较；回滚程序在临时修改版副本中真实执行，逐文件恢复原始字节并清除本次新增源码文件。
- 真实手机仅覆盖安装、启动及核对哈希/版本；没有切换手机 USB、密度或系统全局主题。UI 自动主题切换测试在本次专门启动的模拟器中进行，测试后恢复其原系统主题。

## 记录与复现

- `baseline/` + `baseline-sha256.json`：修改前源文件及 1.0.1 APK 原始字节。
- `dark-mode.patch` + `changes.json`：本次补丁与修改前后哈希。
- `verification.json`：基线/修改版测试、APK、lint、补丁及回滚验证记录。
- `build.log`：最终构建与签名检查输出。
- `phone-install.json`：最终真机安装包核对。

```powershell
python usb_control_app/tests/dark_mode_ui.py  # 仅测试 emulator-5554；需预先安装新版并选择一张背景
python docs/usb-control-dark-mode/verify.py
```

### 源码回滚

```powershell
python docs/usb-control-dark-mode/rollback.py --root F:/Project/07_Androd/08_FanQie
```

该命令先核对所有当前修改文件和封存基线哈希，再还原本次修改；与预期不一致则在写入前停止。此命令**不操作手机、不改系统设置、不卸载 App**，旧 APK 保留在 `releases/usb-control-v1.0.1.apk`。回滚实际执行验证在临时副本完成，当前工作区保留新版。

### 过程中修复

首次 UI 检查发现新增中文字符串经过 PowerShell 管道编码被替换为问号，已修正为 UTF-8、重建并重新覆盖安装。UI 测试还修正了 XML 叶节点真假判断、控件滚动定位和启动动画等待；最终整套外观测试通过。
