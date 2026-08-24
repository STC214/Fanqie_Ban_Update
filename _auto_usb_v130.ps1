$ErrorActionPreference='Continue'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path;$log=Join-Path $root '_usb_v130_deploy.log'
"[$(Get-Date -Format o)] waiting"|Set-Content $log
adb wait-for-device
$zip=Join-Path $root 'usb_debug_toggle-v1.3.0.zip'
adb push $zip /data/local/tmp/usb_debug_toggle-v1.3.0.zip 2>&1|Add-Content $log
if($LASTEXITCODE-ne 0){exit 1}
adb shell su -c "ksud module install /data/local/tmp/usb_debug_toggle-v1.3.0.zip" 2>&1|Add-Content $log
if($LASTEXITCODE-ne 0){exit 2}
adb reboot;adb wait-for-device
$deadline=(Get-Date).AddMinutes(5);do{Start-Sleep 4;$b=(adb shell getprop sys.boot_completed 2>$null|Out-String).Trim()}while($b-ne'1'-and(Get-Date)-lt$deadline)
Start-Sleep 10
adb shell su -c "cat /data/adb/modules/usb_debug_toggle/module.prop; sh /data/adb/modules/usb_debug_toggle/action.sh status" 2>&1|Add-Content $log
