param([string]$TargetRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root=[IO.Path]::GetFullPath($TargetRoot).TrimEnd('\')+'\'
$archive=Join-Path (Split-Path -Parent $PSScriptRoot) 'docs/usb-control-system-apps/rollback-originals.zip'
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try {
    $reader=New-Object IO.StreamReader($zip.GetEntry('manifest.json').Open())
    try {$manifest=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
    # Check every destination and original before writing anything.
    foreach($item in $manifest){
        $dest=[IO.Path]::GetFullPath((Join-Path $root $item.path))
        if(-not $dest.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw 'Rollback target leaves root'}
        if(Test-Path -LiteralPath $dest){
            $current=(Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
            if($current -ne $item.modified -and $current -ne $item.original){throw "File changed since release: $dest"}
        }
        if($item.original){
            $stream=$zip.GetEntry($item.path).Open();$sha=[Security.Cryptography.SHA256]::Create()
            try{$hash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
            if($hash -ne $item.original){throw "Corrupt original: $($item.path)"}
        }
    }
    foreach($item in $manifest){
        $dest=[IO.Path]::GetFullPath((Join-Path $root $item.path))
        if($item.original){
            New-Item -ItemType Directory -Force (Split-Path -Parent $dest)|Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry($item.path),$dest,$true)
            if((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $item.original){throw 'Restored hash mismatch'}
        }elseif(Test-Path -LiteralPath $dest){Remove-Item -LiteralPath $dest -Force}
    }
    Write-Output 'PASS: source and release rollback; all restored hashes verified'
    Write-Output 'Phone downgrade (optional, preserves app data): adb install -r -d releases/usb-control-v1.0.5.apk'
}finally{$zip.Dispose()}
