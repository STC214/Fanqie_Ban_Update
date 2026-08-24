$ErrorActionPreference = "Stop"
$zip = Join-Path $PSScriptRoot "fanqie_update_blocker-v1.1.0.zip"
if (-not (Test-Path -LiteralPath $zip)) { throw "missing module zip: $zip" }
adb push $zip /data/local/tmp/fanqie_update_blocker-v1.1.0.zip
if ($LASTEXITCODE -ne 0) { throw "adb push failed" }
adb shell su -c "ksud module install /data/local/tmp/fanqie_update_blocker-v1.1.0.zip"
if ($LASTEXITCODE -ne 0) { throw "module install failed" }
Write-Host "Installed. Reboot the device to activate v1.1.0."
