param([string]$Sdk='F:\Android\Sdk')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$dir=Join-Path $root 'build/dialog-test'
New-Item -ItemType Directory -Force $dir,"$dir/classes","$dir/dex" | Out-Null
$tools=Join-Path $Sdk 'build-tools/35.0.0'
$android=Join-Path $Sdk 'platforms/android-36/android.jar'
function Run([string]$Tool,[string[]]$Arguments){
    $old=$ErrorActionPreference
    try{$ErrorActionPreference='Continue';& $Tool @Arguments;$code=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
    if($code -ne 0){throw "Dialog test build failed: $Tool ($code)"}
}
Run "$tools/aapt2.exe" @('link','-o',"$dir/base.apk",'-I',$android,'--manifest',"$PSScriptRoot/dialog_device/AndroidManifest.xml")
Run 'javac' @('--release','8','-Xlint:-options','-encoding','UTF-8','-classpath',"$android;$root/build/classes.jar",'-d',"$dir/classes","$PSScriptRoot/dialog_device/SystemAppsDialogInstrumentation.java")
Run 'jar' @('cf',"$dir/classes.jar",'-C',"$dir/classes",'.')
Run "$tools/d8.bat" @('--min-api','26','--lib',$android,'--output',"$dir/dex","$dir/classes.jar")
Copy-Item -LiteralPath "$dir/base.apk" -Destination "$dir/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::Open("$dir/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,"$dir/dex/classes.dex",'classes.dex')}finally{$z.Dispose()}
Run "$tools/zipalign.exe" @('-f','4',"$dir/unsigned.apk","$dir/aligned.apk")
Run "$tools/apksigner.bat" @('sign','--ks',"$env:USERPROFILE/.android/usbcontrol-local.jks",'--ks-key-alias','usbcontrol','--ks-pass','pass:android','--key-pass','pass:android','--out',"$dir/dialog-test.apk","$dir/aligned.apk")
Write-Output "$dir/dialog-test.apk"
