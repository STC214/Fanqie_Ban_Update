$ErrorActionPreference = 'Stop'
adb shell su -c "ksud module uninstall usb_debug_toggle"
if ($LASTEXITCODE -ne 0) { throw 'module uninstall failed' }
adb reboot
adb wait-for-device
Write-Host 'Root USB Debug Toggle removed; installation-time state restored.'
