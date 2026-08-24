# 番茄小说升级屏蔽模块验证记录

## 对象
- 包名：`com.dragon.read`
- APK：`4.9.0.99 (versionCode 490)`
- 原件 SHA-256：`048C20F0963F594E72D754422F625B005CE5228B8D585C50E55D86CCD0CC1535`
- 最终模块：`fanqie_update_blocker-v1.1.0.zip`

## 基线（设备，2026-08-24 17:07:42 +08:00）
输入命令：
```text
adb shell su -c "appops get --user 0 com.dragon.read REQUEST_INSTALL_PACKAGES"
```
字面输出：
```text
No operations.
Default mode: default
```
退出状态：`0`

输入命令：
```text
adb shell su -c "pm list packages --user 0 com.dragon.read"
```
字面输出：
```text
package:com.dragon.read
```
退出状态：`0`

输入命令：
```text
adb shell su -c "ping -c 1 -W 1 ichannel.snssdk.com"
```
字面输出（首两行）：
```text
PING ichannel.snssdk.com.queniuiq.com (182.40.130.77) 56(84) bytes of data.
64 bytes from 182.40.130.77: icmp_seq=1 ttl=54 time=41.8 ms
```
退出状态：`0`

## 首轮安装与设备实测（v1.0）
输入命令：
```text
adb shell su -c "ksud module install /data/local/tmp/fanqie_update_blocker-v1.0.0.zip"
```
字面输出末尾：
```text
- Target package: com.dragon.read
- Blocking dedicated update endpoints
- Done
- Module installed successfully!
```
退出状态：`0`

重启后的模块日志字面输出：
```text
timestamp=2026-08-24T17:10:19+08:00
package=com.dragon.read
request_install_packages=REQUEST_INSTALL_PACKAGES: ignore;
update_activity=com.ss.android.update.UpdateProgressActivity
hosts=
```
结论：安装 AppOp 已从 `default` 变为 `ignore`，主升级 Activity 已进入 disabledComponents。首次 hosts 挂载未在该环境显示，因此 v1.1 增加了按目标 UID 的 iptables TLS/HTTP/QUIC 主机名规则作为独立执行层。

## v1.1 静态与可复现验证
输入命令：
```text
python -c "import zipfile; print(zipfile.ZipFile('fanqie_update_blocker-v1.1.0.zip').testzip())"
```
字面输出：
```text
None
```
退出状态：`0`

输入命令：
```text
bash -n fanqie_update_blocker/service.sh
bash -n fanqie_update_blocker/uninstall.sh
```
字面输出：空。
退出状态：`0`, `0`

输入命令：
```text
git -C PATCH_VERIFY apply --check fanqie_update_blocker.patch
git -C PATCH_VERIFY apply fanqie_update_blocker.patch
```
字面输出：空。
退出状态：`0`, `0`

ZIP SHA-256：`df3892c27d936f82dcb9da2a1d7c90b6f799295a68d6b1999dc90bc2bd5a3a33`

## 回滚
主机命令：
```text
powershell -ExecutionPolicy Bypass -File .\rollback.ps1
```
设备动作：`ksud module uninstall fanqie_update_blocker`，重启后 `uninstall.sh` 将 `REQUEST_INSTALL_PACKAGES` 和三个升级组件恢复为 `default-state`，并删除 `FQ_UPD_BLOCK` 的 IPv4/IPv6 规则链；系统化 hosts 随模块挂载撤销。

## 当前设备状态说明
v1.0 已完成安装并验证 AppOp/组件行为；随后设备从 ADB 设备列表物理消失。v1.1 ZIP、补丁、回滚脚本和原件备份均已在主机完成并复开验证，最终 ZIP 等待设备再次出现在 `adb devices` 后可覆盖安装。
