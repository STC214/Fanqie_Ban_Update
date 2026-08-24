# v2.1.3 mountless 审查与真机验证记录

日期：2026-08-24（Asia/Shanghai）

## 问题

SukiSU/KernelSU 4.2.0 将包含 `system/` 且没有 `skip_mount` 的普通模块报告为 `mount=true`。设备没有 `/data/adb/metamodule`，因此管理器提示模块未挂载/未安装元模块。

v2.1.2 真机基线：

``text
version=2.1.2
mount=true
/data/adb/metamodule: No such file or directory
``

## 修改

- 删除模块内 `system/etc/hosts` 覆盖及整个 `system/` 目录；
- 加入根目录 `skip_mount`；
- `customize.sh` 安装时再次创建 `skip_mount`；
- 保留按目标 UID 生效的 IPv4/IPv6 防火墙、LSPosed URL/域名钩子、组件禁用、AppOp 和偏好清理；
- 版本更新为 2.1.3 / versionCode 213。

该模块现在是 mountless 模块，不需要安装元模块，也不改变设备级 hosts。

## 主机验证

- ZIP 条目检查：`HAS_SYSTEM=False`；包含 `skip_mount`；
- ZIP CRC：`None`；
- `bash -n customize.sh service.sh uninstall.sh`：退出状态 0；
- PowerShell AST：安装、自动部署、回滚脚本全部通过；
- `git apply --check` 与 `git apply`：退出状态 0。

## 真机验证

设备：`23117RK66C`；ksud：`4.2.0-rc1-28-gb2ac2fc8`。

重启后的模块状态：

``text
version=2.1.3
versionCode=213
mount=false
enabled=true
system/: No such file or directory
skip_mount: present
``

功能状态：

- `hook_scope_rc=0`；
- `REQUEST_INSTALL_PACKAGES: ignore`；
- IPv4 和 IPv6 OUTPUT 均存在 `--uid-owner 10317 -j FQ_MEDIA_BLOCK`；
- 更新、视频、小游戏/Miniapp/Unity 组件仍处于禁用状态；
- v2.1.2 已实测“检查更新”不弹窗、短视频入口消失、“我的”页小游戏入口消失；v2.1.3 未改变这些 Hook、组件和防火墙规则，只移除了未实际挂载的 hosts 层。

管理器提示的根因已经消除：`ksud module list` 对该模块返回 `mount=false`。

## 产物与哈希

- `fanqie_update_video_game_blocker-v2.1.3.zip`
  - SHA-256：`2773A5406069E88A9E15A85517CF300ED5B68B4390DF3E5FC531E022900149A5`
- `fanqie_update_video_game_blocker-v2.1.3.patch`
  - SHA-256：`825600D0768A472FED796FFFA52516863F9304B5ABAAEFE74A942C6CD1704962`
- `xposed_hook/fanqie-blocker-hook-v2.1.1.apk`
  - SHA-256：`A632E8FC181110EDAEE108AA103736A4AA56F5E9B5EBAC14A47FE88090EC8193`
- `README.md` / `fanqie_update_blocker/README.md`
  - SHA-256：`3891D870A5A5E654110D405029E469A539B3AC92A9FC3BF19F16C138B980F1C5`
- 回滚：`rollback-v2.ps1`
