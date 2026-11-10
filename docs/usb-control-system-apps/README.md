# 轻控 1.0.6：可勾选卸载系统应用

入口：主页「05 / 系统应用管理」。默认全不选 → 勾选 → 下一步 → 确认执行；恢复使用独立的同类流程。

## 交付物

- 安装包：`../../releases/usb-control-v1.0.6.apk`，同目录提供 SHA-256。
- 源码补丁：`changes.patch`（已执行 `git apply --reverse --check`）。
- 验证：`verification.json`，包含基线/新版命令、输入、原样标准输出、错误输出与退出状态；`release-commands.json` 含构建、安装、校验、补丁及回滚验证命令。
- 回滚：`../../scripts/rollback-usb-control-system-apps.ps1`，原始文件封装在 `rollback-originals.zip`，恢复前后校验 SHA-256。已在独立副本执行并验证旧 APK 签名。默认恢复项目源码和发布文件，不自动降级手机。手机可另执行 `adb install -r -d releases/usb-control-v1.0.5.apk`。

## 行为与验证边界

`SystemApps.java` 新增固定包名白名单、系统包身份检查、按用户卸载/恢复分支、逐项读回与失败继续；`MainActivity.java` 新增勾选、二次确认及结果展示。保留 Root 和原有工具架构，不添加权限。

21 项模拟测试覆盖选中包卸载/恢复、未选包不变、单项失败继续、假成功读回、陈旧列表、重复选择、未知包与恶意包名拒绝、负用户编号及异常列表。原有 model/adapter 测试亦通过。

真机覆盖安装更新、启动、Root 连接、功能卡片、默认不勾选、浏览器选择与卸载确认（取消）、快应用恢复选择（取消）。截图见本目录 `phone-*.png`。浏览器与系统广告原先已安装，快应用原先已卸载；测试后均保持原状态。没有对实际系统包执行测试性卸载或恢复，因此实际 ROM 上的修改结果仍需用户操作后读回确认。

当前手机没有 `com.miui.newhome` / `com.miui.contentcenter`；内容中心按候选包名预置适配，尚无本机该项的修改验证。传送门 `com.miui.contentextension` 不作为内容中心卸载。非小米 ROM 的其他包名不做猜测匹配。

UIAutomator 在此手机遇到权限异常/进程终止，已改用 Root 输入配合截图逐步检查，未修改调试安全设置。首轮发布校验遇到旧 `.idsig` 遗留，归档后复验通过。

命令语义参考：[Android 包管理 Shell 实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)：`uninstall -k --user` 保留数据并限定用户；`install-existing --user` 恢复系统仍保留的包。
