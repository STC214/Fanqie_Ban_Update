param(
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'F:\Android\Sdk' }),
    [string]$BuildTools = '35.0.0',
    [string]$Platform = 'android-36'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$tools = Join-Path $Sdk "build-tools/$BuildTools"
$android = Join-Path $Sdk "platforms/$Platform/android.jar"
$javaBin = Split-Path -Parent (Get-Command javac.exe).Source
function Run([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed: $Tool ($LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force $build,"$build/gen","$build/classes","$build/dex" | Out-Null
# Remove stale generated files only after checking the exact workspace build paths.
foreach ($name in @('gen','classes','dex')) {
    $target=[IO.Path]::GetFullPath((Join-Path $build $name))
    $base=[IO.Path]::GetFullPath($build).TrimEnd('\')+'\'
    if (-not $target.StartsWith($base,[StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup leaves workspace.' }
    Get-ChildItem -LiteralPath $target -Force | Remove-Item -Recurse -Force
}
Run "$tools/aapt2.exe" @('compile','--dir',"$root/res",'-o',"$build/resources.zip")
Run "$tools/aapt2.exe" @('link','-o',"$build/base.apk",'-I',$android,'--manifest',"$root/AndroidManifest.xml",'--java',"$build/gen","$build/resources.zip")
$sources = @((Get-ChildItem "$root/src","$build/gen" -Recurse -Filter '*.java').FullName)
Run "$javaBin/javac.exe" (@('--release','8','-encoding','UTF-8','-classpath',$android,'-d',"$build/classes") + $sources)
Run "$javaBin/jar.exe" @('cf',"$build/classes.jar",'-C',"$build/classes",'.')
Run "$tools/d8.bat" @('--release','--min-api','26','--lib',$android,'--output',"$build/dex","$build/classes.jar")
Copy-Item -LiteralPath "$build/base.apk" -Destination "$build/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$build/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$build/dex/classes.dex",'classes.dex') } finally { $zip.Dispose() }
Run "$tools/zipalign.exe" @('-f','4',"$build/unsigned.apk","$build/aligned.apk")
$key = Join-Path $env:USERPROFILE '.android/usbcontrol-local.jks'
if (-not (Test-Path -LiteralPath $key)) {
    New-Item -ItemType Directory -Force (Split-Path -Parent $key) | Out-Null
    Run "$javaBin/keytool.exe" @('-genkeypair','-keystore',$key,'-alias','usbcontrol','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=USB Control Local Build','-noprompt')
}
$release = Join-Path (Split-Path -Parent $root) 'releases/usb-control-v1.0.3.apk'
Run "$tools/apksigner.bat" @('sign','--ks',$key,'--ks-key-alias','usbcontrol','--ks-pass','pass:android','--key-pass','pass:android','--out',$release,"$build/aligned.apk")
Run "$tools/apksigner.bat" @('verify','--verbose',$release)
Run "$tools/zipalign.exe" @('-c','4',$release)
$hash=(Get-FileHash -LiteralPath $release -Algorithm SHA256).Hash
"$hash  usb-control-v1.0.3.apk" | Set-Content -LiteralPath "$release.sha256" -Encoding ASCII
Write-Output "APK: $release"
Write-Output "SHA256: $hash"
