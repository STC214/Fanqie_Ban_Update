# 番茄小说升级、短视频与小游戏屏蔽模块

适用于 SukiSU Ultra / KernelSU 的模块，目标包名为 `com.dragon.read`。模块通过 KernelSU 系统层规则和 LSPosed 进程内钩子共同屏蔽以下功能：

- APP 版本检查、升级弹窗、升级下载和 APK 安装；
- 短视频入口、视频详情页、广告视频页、视频接口和媒体播放；
- “我的”页面下的小游戏入口、小游戏中心、Miniapp/Unity 游戏容器和小游戏网络请求。

当前版本：**2.1.3**。

## 轻控 Android App

新增独立 App **轻控 1.0.0**（Android 8.0+），不依赖安装 USB 开关模块：

- 安装包：[`releases/usb-control-v1.0.0.apk`](releases/usb-control-v1.0.0.apk)
- USB 调试、按系统已存在的设置项适配 USB 安装、最小宽度快捷调整。
- 使用根目录提供的图片作为图标；可选本机图片作为背景，支持等比缩放、拖动取景及透明度实时预览。
- 系统操作需 Root 授权；背景功能不需 Root，无网络权限。
- 源码、构建及兼容说明：[`usb_control_app/README.md`](usb_control_app/README.md)
- 验证与界面截图：[`docs/usb-control-app/`](docs/usb-control-app/)

## 基线信息

| 项目 | 值 |
|---|---|
| 目标包名 | `com.dragon.read` |
| 分析 APK | `original/番茄小说oppo定制.apk` |
| APK 版本 | `4.9.0.99` |
| versionCode | `490` |
| APK SHA-256 | `048C20F0963F594E72D754422F625B005CE5228B8D585C50E55D86CCD0CC1535` |
| 模块 ID | `fanqie_update_blocker` |
| 钩子包名 | `local.fanqie.blocker` |
| 钩子版本 | `2.1.1 (211)` |
| 架构 | `arm64-v8a` |

## 工作原理

模块包含四层规则。单层规则失效时，其余规则仍会继续阻止对应功能。

### 1. 升级屏蔽

- 拦截手动检查更新调用：
  `com.dragon.read.update.d.a(int, OnUpdateStatusChangedListener)`；
- 当 `Dialog.show()` 调用栈来自 `com.dragon.read.update` 或 `com.ss.android.update` 时取消显示；
- 禁用 `UpdateProgressActivity`、华为 UpdateSDK 升级和安装 Activity；
- 将目标 APP 的 `REQUEST_INSTALL_PACKAGES` AppOp 设置为 `ignore`；
- 阻断 `/check_version/`、`/app_alert_check/`、`/appupdate/` 等升级请求；
- 隔离 `update.apk`、`predownload.apk.part` 等已下载升级文件。

### 2. 短视频屏蔽

- 隐藏 `VideoTabFragment` 返回的根视图；
- 隐藏“视频”“短视频”“看视频”“刷视频”“视频专区”“小视频”等入口文字及父容器；
- 禁用 `VideoDetailActivity`、`ExcitingVideoActivity`、TTRewardVideo、TTFullScreenVideo 等组件；
- 拦截视频 Activity、Intent、URL、MP4/M3U8 地址和视频调用栈中的 `MediaPlayer.setDataSource()`；
- 阻断视频 API、视频域名及 TLS/HTTP/QUIC 主机名标记。

### 3. 小游戏屏蔽

- 将 `IMicroGameConfig.getModel().b` 固定为 `false`；
- 拦截 `PolarisMiniGameManager` 中 Context、Intent 和三参数打开方法；
- 隐藏“小游戏”“游戏中心”“玩游戏”“福利小游戏”等入口文字及父容器；
- 禁用 `MiniGameDetailActivity`；
- 禁用 MiniappTab 0–4、MiniappService 0–4、UCAppService 200–203；
- 禁用 `UCLaunchActivity`、`UnityActivity` 和 `UCPlayerActivity`；
- 清空小游戏专用入口偏好，同时保存原始文件用于回滚；
- 阻断 `minigame`、`mini_game`、`microgame`、`game_center`、`fanqie-game` 和 `/api/apps/game/` 请求；
- 屏蔽 APK 中确认的小游戏固定域名。

### 4. 持久化

`service.sh` 每 60 秒按包名、当前 UID 和 Android 用户重新应用：

- AppOp；
- 组件禁用状态；
- IPv4/IPv6 防火墙链；
- 视频与小游戏偏好规则；
- 升级文件隔离。

因此目标 APP 覆盖安装后，只要包名仍为 `com.dragon.read`，规则会再次生效。

## 运行要求

- SukiSU Ultra 或兼容 KernelSU 模块环境；
- `arm64-v8a` 设备；
- Android 6.0 及以上；
- LSPosed 已安装并正常工作；
- 目标 APP 包名为 `com.dragon.read`。

本模块为 **mountless（免挂载）模块**，不包含 `system/` 覆盖目录，不依赖 KernelSU/SukiSU 元模块。

系统层组件、AppOp 和网络规则由 KernelSU 执行；升级弹窗、动态 UI 入口及运行时方法拦截依赖 LSPosed。

## 安装

### 使用管理器安装

在 SukiSU/KernelSU 管理器中选择 `releases/` 下的安装包：

```text
fanqie_update_video_game_blocker-v2.1.3.zip
```

安装完成后重启一次。

### 使用 ADB 安装

```powershell
cd F:\Project\07_Androd\08_FanQie
.\scripts\install-v2.1.ps1
```

也可手动执行：

```powershell
adb push .\releases\fanqie_update_video_game_blocker-v2.1.3.zip /data/local/tmp/
adb shell su -c "ksud module install /data/local/tmp/fanqie_update_video_game_blocker-v2.1.3.zip"
adb reboot
```

安装脚本会完成以下操作：

1. 安装 KernelSU 模块；
2. 安装内置钩子 APK `local.fanqie.blocker`；
3. 将钩子写入 LSPosed 数据库；
4. 将作用域固定为 `com.dragon.read` 用户 0；
5. 重启设备。

## 验证

设备启动完成后执行：

```powershell
adb shell su -c "cat /data/adb/modules/fanqie_update_blocker/verification.log"
```

日志中应包含：

```text
hook_package=package:.../local.fanqie.blocker.../base.apk
hook_scope_rc=0
request_install=REQUEST_INSTALL_PACKAGES: ignore
```

检查组件：

```powershell
adb shell su -c "dumpsys package com.dragon.read | grep -A 140 disabledComponents"
```

检查防火墙：

```powershell
adb shell su -c "iptables -S FQ_MEDIA_BLOCK"
adb shell su -c "ip6tables -S FQ_MEDIA_BLOCK"
```

功能检查：

1. 打开“我的 → 设置 → 关于/检查更新”，确认不出现“现在升级”弹窗；
2. 检查书城及其他页面，确认短视频 Tab 和入口消失；
3. 打开“我的”页面，确认小游戏/游戏中心入口消失；
4. 尝试通过历史深链打开视频或小游戏页面，页面应立即结束或保持空白。

## 模块操作按钮

在支持 `action.sh` 的管理器中点击模块操作按钮，可输出：

- 当前验证日志；
- 钩子 APK 安装路径；
- `FQ_MEDIA_BLOCK` 防火墙链。

## 卸载与回滚

推荐执行：

```powershell
cd F:\Project\07_Androd\08_FanQie
.\scripts\rollback-v2.ps1
```

回滚会：

- 将禁用组件恢复为 `default-state`；
- 将 `REQUEST_INSTALL_PACKAGES` 恢复为 `default`；
- 恢复 CE/DE 用户目录下修改前的偏好文件；
- 将隔离的升级文件恢复到原路径；
- 删除 IPv4/IPv6 防火墙跳转和规则链；
- 删除 LSPosed 模块与作用域记录；
- 卸载 `local.fanqie.blocker`；
- 重启设备完成模块移除。

也可在管理器中卸载模块后重启。

## 文件结构

```text
fanqie_update_blocker/
├── module.prop                 # 模块元数据
├── customize.sh                # mountless 标记和 LSPosed 注册
├── skip_mount                  # 明确声明无需系统挂载/元模块
├── service.sh                  # 启动及 60 秒规则守护
├── action.sh                   # 管理器操作按钮
├── uninstall.sh                # 完整回滚
├── fanqie-blocker-hook.apk     # LSPosed 钩子
├── bin/sqlite3                 # Android 16/arm64 兼容 SQLite CLI
├── SHA256SUMS                  # 模块内部文件校验
└── README.md
```

项目目录按用途分类（源码目录保持原位）：

```text
README.md                       # 项目说明和使用入口
fanqie_update_blocker/           # 主模块
usb_debug_toggle/               # USB 调试切换模块
xposed_hook/                    # Hook 源码、构建产物及依赖
usb_control_app/                # 轻控原生 Android App 源码和构建脚本
original/                      # 原始 APK 与基线副本，均保留
releases/                      # 发布 ZIP 与对应补丁
scripts/                       # 安装、部署、设备回滚、分析脚本
analysis/                      # 清单、Smali 导出、字符串及域名分析
docs/verification/             # 历史基线、哈希及真机验证记录
docs/reorganization/           # 整理清单、备份、差异及验证
logs/                          # 部署日志
```

- 安装包：`releases/fanqie_update_video_game_blocker-v2.1.3.zip`
- USB 模块安装：`.\scripts\install-usb-debug-toggle.ps1`
- USB 模块回滚：`.\scripts\rollback-usb-debug-toggle.ps1`
- 分析扫描：`python .\scripts\scan_update_strings.py`，也支持传入 APK 路径；默认读取 `original/fanqie_baseline_v490.apk`，与当前工作目录无关。
- 历史分析输出、旧补丁和真机记录原样保留，其中旧路径表示当时的执行位置。
- 撤销本次整理：`.\scripts\rollback-directory-layout.ps1`，仅处理项目文件，不执行设备操作。整理后若文件被编辑，先停止以保护新内容。

## 审查结果

本次审查已确认或修复：

- ZIP 结构和 CRC；
- APK v1/v2/v3 签名；
- Xposed 入口及所有匿名 Hook 类均在 DEX 中；
- 中文入口文字改用 Java Unicode 转义，避免 Windows 代码页将其写成 `?`；
- 所有硬编码组件均存在于基线 APK 清单；
- SQLite 二进制已在 Android 16 arm64 真机执行；
- LSPosed 新版 `modules/modules_state/scope` 数据库结构已适配，并保留旧结构分支；
- 防火墙回滚会按实际 OUTPUT 规则删除带 owner 匹配的跳转；
- Android 16 上使用 `pm list packages -U` 提取目标 UID，已验证防火墙链能够建立；
- 移除 `system/` hosts 覆盖并加入 `skip_mount`，SukiSU 管理器显示 `mount=false`，无需元模块；
- CE、DE 偏好备份使用完整镜像路径，避免多用户同名文件互相覆盖；
- 升级文件隔离保留原始路径，卸载时可恢复；
- `customize.sh`、`service.sh`、`uninstall.sh` 均通过 shell 语法检查；
- 补丁通过 `git apply --check` 和实际应用验证。

## 已知影响与边界

- 禁用 Miniapp/Unity 容器会同时影响 APP 内依赖这些容器的其他小程序功能；
- 禁用奖励视频后，依赖“观看视频领取奖励”的功能也会停止；
- 域名与 URL 阻断通过按目标 UID 生效的 iptables/ip6tables 规则和运行时钩子完成，不修改设备级 hosts；
- 如果后续版本改变包名、更新控制器类名或彻底替换 UI 实现，需要重新分析并更新钩子；
- 目标 APP 的普通阅读、书架和音频功能未列入屏蔽目标，但服务器将某些资源复用到被屏蔽域名时，相应资源可能受影响。

## 日志与排查

模块日志：

```text
/data/adb/modules/fanqie_update_blocker/verification.log
```

LSPosed 日志中应出现：

```text
FanqieBlocker: hooks active in com.dragon.read
```

若 UI 入口仍出现，依次检查：

1. `pm path local.fanqie.blocker` 是否有输出；
2. LSPosed 中该模块是否启用；
3. 作用域是否包含 `com.dragon.read`；
4. 目标 APP 是否在模块启用后被强制停止或设备是否已重启；
5. `verification.log` 中 `hook_scope_rc` 是否为 `0`。
