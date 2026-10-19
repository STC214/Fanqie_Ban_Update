param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
function Resolve-ProjectPath([string]$Relative) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $Relative))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path leaves project: $Relative"
    }
    return $path
}
$audit = Resolve-ProjectPath 'docs/reorganization'
$manifest = Get-Content -LiteralPath (Join-Path $audit 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
# Preflight every file before moving anything; never overwrite newer work.
foreach ($item in $manifest.moves) {
    $src = Resolve-ProjectPath $item.destination
    $dst = Resolve-ProjectPath $item.source
    if (Test-Path -LiteralPath $dst) { throw "Destination exists: $dst" }
    if ((Get-FileHash -LiteralPath $src -Algorithm SHA256).Hash -ne $item.organized_sha256) {
        throw "File changed since organization: $src"
    }
}
if ((Get-FileHash -LiteralPath (Join-Path $root 'README.md')).Hash -ne $manifest.readme_sha256) {
    throw 'README changed since organization.'
}
foreach ($name in $manifest.backups) {
    $backup = Resolve-ProjectPath "docs/reorganization/backup/$name"
    if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) { throw "Missing backup: $name" }
    $expected = if ($name -eq 'README.md') { $manifest.original_readme_sha256 } else {
        ($manifest.moves | Where-Object source -eq $name).sha256
    }
    if ((Get-FileHash -LiteralPath $backup).Hash -ne $expected) { throw "Backup hash mismatch: $name" }
}
foreach ($item in $manifest.moves) {
    Move-Item -LiteralPath (Resolve-ProjectPath $item.destination) -Destination (Resolve-ProjectPath $item.source)
}
foreach ($name in $manifest.backups) {
    Copy-Item -LiteralPath (Resolve-ProjectPath "docs/reorganization/backup/$name") -Destination (Resolve-ProjectPath $name) -Force
}
foreach ($item in $manifest.moves) {
    if ((Get-FileHash -LiteralPath (Resolve-ProjectPath $item.source)).Hash -ne $item.sha256) {
        throw "Restored hash mismatch: $($item.source)"
    }
}
Write-Output "Restored $($manifest.moves.Count) files; all original hashes match. Audit records retained."
