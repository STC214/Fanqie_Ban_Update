# 轻控 1.0.5 发布前重复审查

日期：2026-11-05。检查范围：新增一键循环壁纸、Root 桥接、原有功能回归、打包、回滚和安装。按用户要求不截图、不进行界面查看。播放效果不是代码测试的保证项。

## 第一轮：事务与异常恢复

已用 `wallpaper_regression.py --baseline` 重现：旧实现遇到损坏的撤销日志仍继续改名，导致之后的撤销受阻。

修复：
- 改名和撤销均先校验整份日志，包括完整行、哈希、严格文件名和末尾换行。损坏时先停止，不再改名。
- 写前日志落盘后才改名，成功后同步；撤销先同步改名与待保留记录，再原子替换日志并同步。
- 撤销也校验还原后的 SHA256，失败或冲突保留记录。
- 参数、任务类型和锁文件类型增加检查，避免 FIFO 锁使任务挂起。

结果：`regression-baseline.json` 证明旧问题，`regression.json` 证明修复及中断恢复通过。

## 第二轮：Root、目录与回归

- 目录固定为主用户 0 的主题管理视频壁纸目录；其他 Android 用户被拦截。
- 在 App 写入脚本前校验私有目录的真实路径，拒绝指向其他目录的符号链接。设备测试确认目标目录仍为空。
- 同签名测试 APK 直接调用最终轻控内的 `Root.run` 和 `WallpaperLoop.run`，不启动 Activity、不调用 UI 自动化。验证应用 UID 获得 Root、资源脚本正确提取、真实目录处理为 0 个改名 / 5 个已规范、撤销无待处理项，全部真实文件名和 SHA256 不变。
- `device-integration.json` 含逐条结果，`functional/verification.json` 含改名、幂等、重名冲突、跳过规则、撤销和锁排他验证；测试包执行后卸载。

## 第三轮：重复复验与发布门禁

- `latest/verification.json`：34 条基础能力/显示/图片断言、420 条事务模型断言（20 轮循环）、23 条 USB 安装与恢复断言，共 477 条通过。
- 新增恢复回归重跑通过：损坏日志、尾行截断、路径穿越、非法哈希、写前日志中断前/后、多批次累计撤销、重复执行、大小写保留、符号链接、FIFO 锁及参数错误。
- APK 签名、对齐、ZIP 完整性、清单版本、SHA256、DEX 中生产类、APK 内资源脚本与被测源码逐字节一致通过。
- 历史包保留在基线 ZIP 和本地归档，releases 目录只保留当前轻控版本；其他模块未改动。
- 打包复查修复 Git 自动换行可能改变发布补丁字节的问题：为轻控补丁与校验清单添加保留字节属性；补丁语法的空格标记不作为代码空白错误。发布前核对 Git blob 与本地资产哈希一致。
- 当前源码反向补丁在独立副本实际执行后与最初基线逐字节一致；基线 APK 从 ZIP 提取并验证哈希，真机回装旧版及再安装最终版通过，见最终交付记录。
- CRLF-aware Git 空白检查通过。当前包与测试范围内未发现未解决缺陷；不作“所有设备绝对无缺陷”的保证。

## 重跑

```powershell
.\usb_control_app\build.ps1
python scripts/verify-usb-control-latest.py --out docs/usb-control-wallpaper/review/latest
python usb_control_app/tests/wallpaper_phone.py --out docs/usb-control-wallpaper/review/functional
python usb_control_app/tests/wallpaper_regression.py
.\usb_control_app\tests\build-wallpaper-test.ps1
.\docs\usb-control-wallpaper\rollback.ps1 -Mode Verify
```

安装与发布的版本、哈希、Git 提交及资产校验记录见 `final-delivery.json` 和 `publication.json`。
