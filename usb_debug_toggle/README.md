# Root USB Debug Toggle

一个 mountless SukiSU/KernelSU 模块。安装后无需进入开发者选项，直接在模块列表点击本模块的“操作/Action”按钮即可切换 USB 调试。

## 使用

1. 安装 `usb_debug_toggle-v1.0.0.zip`；
2. 重启；
3. 打开 SukiSU → 模块 → Root USB Debug Toggle；
4. 点击“操作/Action”：当前开启则关闭，当前关闭则开启。

模块仅切换 USB 调试，不启用无线调试，不删除已有 ADB 授权。首次连接一台新电脑时，Android 原有的 RSA 授权确认仍会显示。

命令行也可执行：

```sh
su -c 'sh /data/adb/modules/usb_debug_toggle/action.sh status'
su -c 'sh /data/adb/modules/usb_debug_toggle/action.sh on'
su -c 'sh /data/adb/modules/usb_debug_toggle/action.sh off'
```

状态日志：`/data/adb/modules/usb_debug_toggle/usb_toggle.log`。

卸载模块时会恢复安装前记录的 USB 调试、开发者选项和 USB 功能状态。
