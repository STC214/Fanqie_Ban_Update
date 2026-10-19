param([string]$Manifest=(Join-Path (Split-Path -Parent $PSScriptRoot) '.local/release-cleanup/manifest.json'),[string]$DestinationRoot=(Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
$m=Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
$archive=[IO.Path]::GetFullPath($m.archive).TrimEnd('\')+'\'
$root=[IO.Path]::GetFullPath($DestinationRoot).TrimEnd('\')+'\'
$items=@()
foreach($e in $m.files){
    if([IO.Path]::IsPathRooted($e.path)){throw 'Absolute manifest path rejected'}
    $source=[IO.Path]::GetFullPath((Join-Path $archive $e.path));$dest=[IO.Path]::GetFullPath((Join-Path $root $e.path))
    if(-not $source.StartsWith($archive,[StringComparison]::OrdinalIgnoreCase) -or -not $dest.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw 'Path leaves intended root'}
    if(-not (Test-Path -LiteralPath $source) -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $e.sha256){throw "Archive missing or damaged: $($e.path)"}
    if((Test-Path -LiteralPath $dest) -and (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $e.sha256){throw "Destination differs: $($e.path)"}
    $items+= [pscustomobject]@{source=$source;dest=$dest;hash=$e.sha256}
}
foreach($e in $items){
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $e.dest) | Out-Null
    Copy-Item -LiteralPath $e.source -Destination $e.dest -Force
    if((Get-FileHash -LiteralPath $e.dest -Algorithm SHA256).Hash -ne $e.hash){throw 'Restoration hash mismatch'}
}
Write-Output "PASS: restored $($items.Count) historical files; latest release untouched"
