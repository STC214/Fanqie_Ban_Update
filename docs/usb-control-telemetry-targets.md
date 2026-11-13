# 轻控 1.0.9：运营商注册组件

日期：2026-11-13。仅扩充现有系统应用卸载 / 恢复白名单，沿用轻控弹窗。

来源：本地克隆的 [TelemetryCleaner](https://github.com/Eynnzerr/TelemetryCleaner)，提交 `3938158165a9332c9faa6c433b87c9c700c801f9`。
核对文件：`app/src/main/java/com/eynnzerr/telemetrycleaner/data/PackageDirectory.kt` 的 `groups`，以及 `README.md` 的组件表。
导入完整 13 项目录，不是 `autoCleanupNames` 的 4 项子集。只采用包名和描述，不引入其运行时代码或依赖。

| 厂商 | 运营商 / 功能 | 精确包名 | 上游清单标记 |
|---|---|---|---|
| 跨厂商 | 联通设备注册 | `com.mobiletools.systemhelper` | 已实测 |
| OPPO / 一加 / realme | 电信注册 | `com.oppo.ctautoregist` | 已实测 |
| OPPO / 一加 / realme | 移动注册 | `com.coloros.regservice` | 已实测 |
| 小米 / Redmi / POCO | 电信注册 | `com.xiaomi.registration` | 已实测 |
| 小米 / Redmi / POCO | 移动注册 | `com.miui.dmregservice` | 已实测 |
| vivo | 移动 / 电信注册 | `com.vivo.devicereg` | 已实测 |
| 华为及旧荣耀 | 电信数据注册 | `com.huawei.android.UEInfoCheck` | 未验证 |
| 华为及旧荣耀 | 电信短信注册 | `com.huawei.android.AutoRegSms` | 未验证 |
| 华为 | 移动注册 | `com.huawei.regservice` | 未验证 |
| 荣耀 | 电信数据注册 | `com.hihonor.android.UEInfoCheck` | 未验证 |
| 荣耀 | 电信短信注册 | `com.hihonor.android.AutoRegSms` | 未验证 |
| 荣耀 | 设备注册（移动归属推断） | `com.hihonor.regservice` | 未验证 |
| 魅族 / Flyme | 设备注册（归属待核验） | `com.meizu.regservice` | 未验证 |

上游表格的实测标记不代表轻控已经在各厂商设备实测，且上游正文还注明作者本人仅验证了 SystemHelper 和 OPPO 组件。轻控保留 7 项未验证提示，不承诺卸载后所有运营商功能正常。

## 行为约束

- 保留原有 5 个候选包名，共 18 个精确包名，无通配符；未预装或非系统包不会出现在弹窗中。
- 默认不勾选，选择后再次确认；只操作轻控所在 Android 用户，不自动遍历双开空间。
- 卸载：`pm uninstall --user USER_ID PACKAGE`，不带 `-k`，删除该用户的应用数据。
- 恢复：`cmd package install-existing --user USER_ID PACKAGE`，仅使用系统保留的预装包，不恢复已删除数据。
- 执行前重新确认系统包身份，每项操作后精确读回安装状态；其他包不受影响。
- 不引入自动清理、定时后台任务、Shizuku 或网络权限。不把组件是否恶意作为导入前提。

## 验证与回退

本次公开验证记录：`usb-control-telemetry-review/verification.json`，包含修改前后命令、输出与退出码；临时诊断保留在忽略的 `.local/telemetry-targets/`。
新增回归覆盖 13 个包在用户 0 / 999 的卸载、删除数据、恢复不找回数据、非系统包过滤及无关应用隔离。
弹窗测试使用生产目录的 18 项真实名称 / 包名，覆盖浅暗色、大字体、选择 / 确认及横屏。使用独立 Android 15 模拟器运行；没有连接实体手机，本次不实际卸载 / 恢复手机系统应用，不将模拟结果称为各厂商实测。

源码补丁：`../releases/usb-control-v1.0.9.patch`。
原文件及 SHA-256：`usb-control-telemetry-review/rollback-originals.zip`。
源码回退：`python scripts/rollback-usb-control-telemetry.py --check` 预检，`--apply` 执行，`--self-test` 在临时目录验证回退。
回退只恢复本次修改的项目文件，不操作手机，也不恢复卸载数据；新增 1.0.9 安装包保留供核对。
1.0.8 安装包保留为基线；本次不自动执行手机 APK 降级或安装。发布说明见 `usb-control-telemetry-review/RELEASE_NOTES.md`。
