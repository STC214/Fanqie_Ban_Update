# 轻控 1.0.2

一个轻量、离线、中文原生 Android 工具。Java + Android SDK，无第三方运行时、无 WebView、无广告或网络权限。最低 Android 8.0 / API 26，目标 API 36。

## 安装

安装 `../releases/usb-control-v1.0.2.apk`，打开「轻控」，在 SukiSU / KernelSU / Magisk 管理器中授予 Root。无需先安装原 USB 调试模块；已安装时仍可继续使用，切换后会刷新旧模块的状态描述。

首次成功连接 Root 时将初始系统值原子保存到 `system-baseline.properties`，缓存写入或恢复脚本生成失败时停止启用系统操作，之后不把修改后的值覆盖成初始值。没有 Root 或读取失败时，系统开关置灰；选图、取景、透明度调整仍正常使用，且图片任务与 Root 队列相互独立。

## 功能

### 连接控制

- **USB 调试**：读写 `global/adb_enabled`，开启时启用开发者选项；关闭前确认提示会中断 USB ADB。不自动开启无线调试，不删除 RSA 授权。
- **USB 安装**：检测 `global` / `secure` / `system` 表中已存在且为 0/1 的 `adb_install_enabled`；恰好一个候选时启用，缺失、异常或歧义时置灰。写入后读取校验。
- 这是保守适配，不声称覆盖全部 ROM。独立开关不属于统一 Android API；使用私有服务、账号校验或其他存储的厂商系统会置灰。不会创建猜测键，不把安装验证器、未知来源或「USB 调试安全设置」当作 USB 安装开关。现有键的读回成功不等于厂商安装器全链路已验证。

### 最小宽度

- 同时显示 dp 和当前逻辑 dpi；可输入 280–720 dp 或使用 360 / 393 / 411 / 480 预设。
- 按当前有效短边像素计算 `dpi = round(shortSidePx * 160 / smallestWidthDp)`，使用 `wm density`，不修改物理分辨率。
- 约 20 秒独立 Root 看门狗：每次预览独立脚本和持久化日志，以原子文件改名决出确认/超时的唯一赢家，恢复后读取校验。前台预览保持亮屏，后台以 `/proc/uptime` 计时，休眠唤醒后立即检查超时，最多约一秒轮询延迟。点击「保留」才提交；也可恢复系统默认。
- 看门狗不跨手机重启运行；若预览期间手机重启，下次打开 App 会恢复未确认的旧密度。应先确认或还原后再重启。
- 折叠屏、桌面模式、多屏及厂商额外显示缩放可能改变系统最终 dp，当前针对默认主显示屏。

### 自定义背景

- 使用系统文件选择器，选取手机上的图片；不索取相册全量访问或存储权限。
- 图片由 Android 解码，长边限制到约 2048 像素，复制到 App 私有目录。Android 9+ 使用 ImageDecoder；Android 8 使用平台 ExifInterface 校正八种方向。解码、持久化均在独立图片队列执行。
- 始终等比 cover 缩放；单指拖动选区，双指等比放大 1–4 倍，不提供变形拉伸。
- 「图片透明度」滑块：0% = 完全显示，100% = 完全透明。实时预览真实主界面卡片叠加效果；白色高不透明度卡片确保文字可读。
- 保存新图片文件并同步后才提交取景与透明度；失败时保留旧图片和设置，保存期间锁定取消与编辑；活动销毁后不再更新旧窗口。取景窗口随旋转调整预览比例。取消不修改已保存背景；删除背景仅删除 App 内副本。
- 图标资源是根目录所给 PNG 的原样副本，不重绘图像。系统桌面按自己的图标蒙版裁切。

## 回滚与卸载

推荐先在 App 点击「恢复首次使用前的系统设置」，再卸载。只卸载 App 不会自动恢复已经提交的系统设置。旧 KernelSU USB 模块仍保持独立生命周期，不会随 App 卸载而删除。

首次 Root 读取后还会生成本机恢复脚本，紧急情况下在手机 Root 终端执行：

```sh
su -c 'sh /data/user/0/local.usbcontrol/files/restore-system.sh'
```

电脑侧（用户 0）恢复并卸载：

```powershell
.\scripts\rollback-usb-control-app.ps1 -Serial DEVICE_SERIAL
```

从未获得 Root、未修改系统时可加 `-UninstallOnly`。恢复脚本来源于本机初始值，不使用通用默认值覆盖手机。

## 构建

```powershell
.\usb_control_app\build.ps1 -Sdk F:\Android\Sdk
```

需 JDK 21（编译 Java 8 字节码）、Android SDK Platform 36、Build Tools 35.0.0。也可通过 `ANDROID_SDK_ROOT` 指定 SDK。无需 Gradle 或下载应用依赖。构建前校验构建路径并清理生成目录，避免旧 class 混入。

输出 `releases/usb-control-v1.0.2.apk` 和 `.apk.sha256`。本地签名密钥为 `%USERPROFILE%\.android\usbcontrol-local.jks`（本地构建口令 `android`），不入仓库；请另行备份以便后续覆盖更新。此口令用于个人本地构建，不是商店发行的密钥管理方案。

## 验证

`tests/LogicTest.java` 覆盖能力检测、歧义、密度换算、参数边界及图像等比缩放。`tests/ui_smoke.py` 在专用无 Root 模拟器测试最终 APK 的禁用状态、背景、透明度预览及持久化。Root 命令集测试使用独立 AOSP userdebug 模拟器，测试专用 `su` 参数适配脚本不包含在交付 APK。

1.0.0 的历史交付记录保留在 `../docs/usb-control-app/`；1.0.1 的逐轮审查、真实 OnePlus / Android 16 Root 回归、签名、DEX 与资源检查见 `../docs/usb-control-audit/`。真机 USB 安装键缺失，已验证置灰；未伪造系统键，不把合成值的读回当作厂商安装策略验证。

技术依据：[AOSP WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/WindowManagerShellCommand.java)、[Android 系统文件选择器](https://developer.android.com/training/data-storage/shared/documents-files)、[RedMagicX 的 USB 安装键实现说明](https://github.com/XPRAMT/RedMagicX)。

### 外观模式（1.0.2）
在「03 / 你的风格」中选择「外观模式」：跟随系统（默认）、浅色、暗色。选择自动记忆；暗色同时适配卡片、文字、系统栏、弹窗及背景透明度预览，不改变图片原色、裁剪位置或透明度。
暗色模式验证记录：`../docs/usb-control-dark-mode/`。
