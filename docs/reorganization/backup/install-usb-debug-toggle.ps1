$ErrorActionPreference = 'Stop'
$zip = Join-Path $PSScriptRoot 'usb_debug_toggle-v1.3.0.zip'
adb push $zip /data/local/tmp/usb_debug_toggle-v1.3.0.zip
if ($LASTEXITCODE -ne 0) { throw 'adb push failed' }
adb shell su -c "ksud module install /data/local/tmp/usb_debug_toggle-v1.3.0.zip"
if ($LASTEXITCODE -ne 0) { throw 'module install failed' }
adb reboot
adb wait-for-device
Write-Host 'Root USB Debug Toggle installed.'
