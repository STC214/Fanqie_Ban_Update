$ErrorActionPreference = "Stop"
adb shell su -c "ksud module uninstall fanqie_update_blocker"
if ($LASTEXITCODE -ne 0) { throw "module uninstall failed" }
adb reboot
adb wait-for-device
Write-Host "Module, hook scope, firewall and component overrides rolled back."
