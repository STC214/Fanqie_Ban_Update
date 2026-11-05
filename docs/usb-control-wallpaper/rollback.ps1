param([ValidateSet('Verify','Source','Wallpapers','Apk')][string]$Mode='Verify')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$patch=Join-Path $PSScriptRoot 'changes.patch'
function BaselineApk {
    # Historical packages are archived; recover exact bytes from the preserved ZIP.
    $directory=Join-Path $root '.local/wallpaper-rollback'
    New-Item -ItemType Directory -Force $directory | Out-Null
    $apk=Join-Path $directory 'usb-control-v1.0.4.apk'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot 'baseline-source.zip'))
    try {
        $entry=$zip.GetEntry('releases/usb-control-v1.0.4.apk')
        if($null -eq $entry){throw 'Preserved APK is missing from baseline ZIP.'}
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$apk,$true)
    } finally {$zip.Dispose()}
    if((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -ne 'AA4A25D98070B7FD7A8C5234C0B94007A28CA23152FD9C80AE2C60B0449898B6'){throw 'Baseline APK hash mismatch.'}
    return $apk
}
Push-Location $root
try {
    if($Mode -eq 'Verify' -or $Mode -eq 'Source') {
        & git apply --reverse --check $patch
        if($LASTEXITCODE -ne 0){throw 'Reverse patch check failed; existing changes preserved.'}
        if($Mode -eq 'Source') {
            & git apply --reverse $patch
            if($LASTEXITCODE -ne 0){throw 'Reverse patch failed.'}
        }
        $null=BaselineApk
        Write-Output 'PASS: reverse patch applicable; baseline APK SHA256 verified.'
    } elseif($Mode -eq 'Wallpapers') {
        # Same cumulative undo operation as the in-app button; no overwrite.
        & adb shell "su -c 'sh /data/user/0/local.usbcontrol/files/wallpaper-loop/wallpaper.sh /sdcard/Android/data/com.android.thememanager/files/MIUI/.videowallpaper /data/user/0/local.usbcontrol/files/wallpaper-loop undo'"
        if($LASTEXITCODE -ne 0){throw 'Wallpaper rollback failed; retain the private journal.'}
    } elseif($Mode -eq 'Apk') {
        $apk=BaselineApk
        & adb push $apk /data/local/tmp/qingkong-rollback.apk
        if($LASTEXITCODE -ne 0){throw 'Baseline APK transfer failed.'}
        $result=& adb shell "su -c 'pm install -r -d /data/local/tmp/qingkong-rollback.apk'"
        $result
        if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Success'){throw 'Baseline APK restore failed.'}
    }
} finally { Pop-Location }
