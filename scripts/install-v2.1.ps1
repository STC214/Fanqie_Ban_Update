$ErrorActionPreference = "Stop"
$zip = Join-Path $PSScriptRoot "../releases/fanqie_update_video_game_blocker-v2.1.3.zip"
adb push $zip /data/local/tmp/fanqie_update_video_game_blocker-v2.1.3.zip
if ($LASTEXITCODE -ne 0) { throw "adb push failed" }
adb shell su -c "ksud module install /data/local/tmp/fanqie_update_video_game_blocker-v2.1.3.zip"
if ($LASTEXITCODE -ne 0) { throw "module install failed" }
adb reboot
adb wait-for-device
Write-Host "v2.1.3 installed; waiting for Android boot."
