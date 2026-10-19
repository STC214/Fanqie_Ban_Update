# 轻控 1.0.3：小米 / HyperOS USB 安装适配

## 问题与修复

这台 Xiaomi 23117RK66C（Android 16，HyperOS OS3.0 / V816）有系统“USB 安装”设置，但 global / secure / system 的 `adb_install_enabled` 均为 null。1.0.2 仅检查该标准键，因此将实际存在的开关置灰。

依据本机系统 Settings / 安全中心 APK 的定点分析，开发者选项调用 `com.android.security.AdbUtils`，通过安全中心 RemoteProvider 读取 `security_adb_install_enable`；关闭使用同一 provider 的布尔 SET，开启进入系统 `AdbInstallVerifyActivity`。该 provider 对开启有专门的调用方检查。

1.0.3 新增 `UsbInstall` 适配器及 `MIUI` 状态段：

- 探测 MIUI 属性、真实键存在性、布尔值和匹配的系统验证组件，全部成立才启用此适配。
- 从安全中心真实状态渲染开关；不把 GET 的默认 false 当成支持证据。
- 关闭使用厂商原有接口，并读回核对；开启进入原有系统验证流程，完成或取消后返回刷新。
- 不编辑系统 XML，不设置假标准键，不关闭账号验证、安装校验、未知来源或 USB 调试安全策略。
- 初始状态保存、恢复命令与恢复脚本同步支持此接口。若要恢复初始开启状态而当前关闭，先引导系统确认；没有确认成功就不会报告恢复成功。
- 标准键路径保留，未识别的系统继续置灰。图标、背景选区与透明度、三档主题均保留。

## 交付

- APK：`../../releases/usb-control-v1.0.3.apk`（103）
- SHA-256：`1D1BFC24FE1B2CBDC2C47F0F96986F06736505008E718654C5147F608311ED3D`
- 补丁：`usb-install.patch`，文件哈希：`changes.json`
- 验证记录：`verification.json`；真机摘要：`phone-summary.json`
- 原始源码及旧 APK：`baseline/`，封存哈希：`baseline-sha256.json`
- 源码回滚：`rollback.py`

## 验证与范围

1. 修改前与修改后分别编译、执行逻辑/事务测试：基线 19 + 420 项，修改版 29 + 420 项；另有 20 项 USB 安装适配及恢复命令断言，全部通过。
2. 真机已覆盖安装最终包并核对已安装 APK SHA-256 一致。观察到实际 provider 关闭值 false、开启值 true；开关从置灰变为可用，并显示厂商适配提示。最终读回开启，与测试前相同。
3. 最终核对 USB 调试为 1、无线调试为 0、物理密度 560 / 覆盖 420，均保持原值；三个标准安装键仍为 null，没有伪造。临时 `local.usbcontrol.audit` 测试组件已卸载。
4. 新旧 APK 签名、对齐、ZIP 完整性通过；图标与旧 APK 一致。lint 0 错误、3 条此前已有提示（平台 EXIF、target36 非最新37、基础 adaptive icon monochrome 提示而 v33 已提供）。
5. 补丁在临时基线副本应用后逐文件比较；回滚脚本在临时修改版副本实际执行，并核对原始字节。
6. 真机已有其他 UiAutomation 会话，未关闭或接管；系统验证跳转期间测试 instrumentation 未产出完整报告，不计作通过的 native 测试。最终采用普通 App 界面观察和系统实际值读回核对。没有将开关读回等同于所有 APK/账号/设备策略都允许安装。

系统 APK 仅在 `.local/usb-install/` 做只读分析，身份哈希见真机摘要；未修改或分发系统 APK。真机原始日志、截图和设备标识留在本机。

## 复现 / 回滚

```powershell
python docs/usb-control-usb-install/verify.py
# 恢复本次源码修改，不操作手机、不卸载 App：
python docs/usb-control-usb-install/rollback.py --root F:/Project/07_Androd/08_FanQie
```

源码回滚预先核对所有修改文件与基线哈希，防止覆盖后续用户编辑。旧安装包 1.0.2 保留。手机系统值恢复可在 App 内选择“恢复首次使用前的系统设置”；小米开启仍需完成系统原有确认。当前工作区与手机保留修复后的 1.0.3。
