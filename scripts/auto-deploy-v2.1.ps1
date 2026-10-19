$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$log = Join-Path $root "../logs/auto-deploy-v2.1.3.log"
"[$(Get-Date -Format o)] waiting for adb device" | Set-Content -Encoding UTF8 $log
adb wait-for-device
"[$(Get-Date -Format o)] device detected" | Add-Content -Encoding UTF8 $log
$zip = Join-Path $root "../releases/fanqie_update_video_game_blocker-v2.1.3.zip"
adb push $zip /data/local/tmp/fanqie_update_video_game_blocker-v2.1.3.zip 2>&1 | Add-Content -Encoding UTF8 $log
if ($LASTEXITCODE -ne 0) { throw "adb push failed" }
adb shell su -c "ksud module install /data/local/tmp/fanqie_update_video_game_blocker-v2.1.3.zip" 2>&1 | Add-Content -Encoding UTF8 $log
if ($LASTEXITCODE -ne 0) { throw "module install failed" }
"[$(Get-Date -Format o)] module installed; rebooting" | Add-Content -Encoding UTF8 $log
adb reboot
adb wait-for-device
$deadline=(Get-Date).AddMinutes(5)
do { Start-Sleep 5; $boot=(adb shell getprop sys.boot_completed 2>$null | Out-String).Trim() } while($boot -ne "1" -and (Get-Date)-lt $deadline)
Start-Sleep 15
adb shell su -c "cat /data/adb/modules/fanqie_update_blocker/verification.log 2>&1" | Add-Content -Encoding UTF8 $log
adb shell su -c "dumpsys package com.dragon.read | grep -A 140 disabledComponents | grep -E 'UpdateProgress|VideoDetail|MiniGame|Miniapp|Unity|UCLaunch'" | Add-Content -Encoding UTF8 $log
"[$(Get-Date -Format o)] deployment complete" | Add-Content -Encoding UTF8 $log
