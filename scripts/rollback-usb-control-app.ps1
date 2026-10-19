param([string]$Serial, [switch]$UninstallOnly)
$ErrorActionPreference = 'Stop'
$device = @()
if ($Serial) { $device = @('-s', $Serial) }
if (-not $UninstallOnly) {
    # Uses the baseline captured on this phone, not guessed desktop defaults.
    & adb @device shell su -c 'if [ -f /data/user/0/local.usbcontrol/files/restore-system.sh ]; then sh /data/user/0/local.usbcontrol/files/restore-system.sh; else echo baseline_missing_use_in_app_restore_or_UninstallOnly; exit 3; fi'
    if ($LASTEXITCODE -ne 0) { throw 'System restore did not complete; app retained.' }
}
& adb @device uninstall local.usbcontrol
if ($LASTEXITCODE -ne 0) { throw 'App uninstall failed.' }
Write-Output 'USB Control app removed. Existing KernelSU modules and original phone images retained.'
