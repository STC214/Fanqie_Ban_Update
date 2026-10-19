param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot),[switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\','/')
function Resolve-InRoot([string]$Relative){
    $p=[IO.Path]::GetFullPath((Join-Path $root $Relative))
    if(-not $p.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Path leaves project: $Relative"}
    return $p
}
$audit=Resolve-InRoot 'docs/usb-control-audit'
$manifest=Get-Content -LiteralPath (Join-Path $audit 'rollback-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($entry in $manifest.files){
    $file=Resolve-InRoot $entry.path
    if($entry.modified){if(-not (Test-Path -LiteralPath $file -PathType Leaf) -or (Get-FileHash -LiteralPath $file).Hash -ne $entry.modified){throw "Modified content differs; retained: $file"}}
    elseif(Test-Path -LiteralPath $file){throw "New content occupies old path: $file"}
    if($entry.original){
        $backup=Resolve-InRoot ("docs/usb-control-audit/baseline/"+$entry.path)
        if((Get-FileHash -LiteralPath $backup).Hash -ne $entry.original){throw "Baseline checksum mismatch: $backup"}
    }
}
if($VerifyOnly){Write-Output 'Rollback preflight PASS; no files changed.';exit 0}
foreach($entry in $manifest.files){
    $file=Resolve-InRoot $entry.path
    if($entry.original){
        New-Item -ItemType Directory -Force (Split-Path -Parent $file) | Out-Null
        Copy-Item -LiteralPath (Resolve-InRoot ("docs/usb-control-audit/baseline/"+$entry.path)) -Destination $file -Force
    }elseif($entry.modified){Remove-Item -LiteralPath $file}
}
foreach($entry in $manifest.files){
    $file=Resolve-InRoot $entry.path
    if($entry.original){if((Get-FileHash -LiteralPath $file).Hash -ne $entry.original){throw "Restore checksum mismatch: $file"}}
    elseif(Test-Path -LiteralPath $file){throw "New source still present: $file"}
}
Write-Output 'Source rollback PASS: original hashes restored. Releases, audit records and phone left unchanged.'
