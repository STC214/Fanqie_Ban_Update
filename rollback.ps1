$ErrorActionPreference = "Stop"
adb shell su -c "ksud module uninstall fanqie_update_blocker"
if ($LASTEXITCODE -ne 0) { throw "module uninstall failed" }
adb reboot
adb wait-for-device
Start-Sleep -Seconds 20
adb shell su -c "appops get --user 0 com.dragon.read REQUEST_INSTALL_PACKAGES"
