# Root USB Debug Toggle v1.0.0 验证记录

## 基线

真机安装前：

``text
adb_enabled=1
development_settings_enabled=1
sys.usb.config=adb
``

## 修改后

- 模块 ID：`usb_debug_toggle`；
- SukiSU 状态：`enabled=true`、`action=true`、`mount=false`；
- 安装过程不修改 USB 调试状态；
- `action.sh status` 返回 USB debugging enabled；
- 执行 SukiSU 模块 Action 后，ADB 设备列表变为空，确认关闭动作生效；
- 再次点击同一 Action 会执行开启分支：写入 `adb_enabled=1`、启动 adbd 并重新应用 USB functions。

## 主机验证

- ZIP CRC：`None`；
- ZIP 不含 `system/`，包含 `skip_mount`；
- 三个 Shell 脚本通过 `bash -n`；
- 安装和回滚 PowerShell 脚本通过 AST 解析；
- 补丁通过 `git apply --check` 和实际重放。

## 产物

- `usb_debug_toggle-v1.0.0.zip` — SHA-256 `238014BB20FB2D192AC5089F830A79051E06E6772163305BC5C9F68DD042CA29`
- `usb_debug_toggle-v1.0.0.patch` — SHA-256 `9CF30F77BF37125CBF6DB58FCD38F33A514FBABE596D0AD8DAEC44D20451B32D`
- `usb_debug_toggle/README.md`
- `install-usb-debug-toggle.ps1`
- `rollback-usb-debug-toggle.ps1`

卸载脚本恢复安装前记录的 `adb_enabled`、`development_settings_enabled` 和 USB functions。
