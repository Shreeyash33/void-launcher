param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

$rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
if (-not (Test-Path -LiteralPath $rootFull -PathType Container)) {
    throw "Workspace root does not exist: $rootFull"
}

$backupDir = Join-Path $rootFull 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$zipPath = Join-Path $backupDir "void-launcher-source-$stamp.zip"

# Generated/VCS folders and the separate web_editor project are omitted.
# Everything else is part of the project snapshot, including downloadable APKs,
# local.properties, and logs.
$excludedPrefixes = @(
    '.git/',
    '.gradle/',
    'build/',
    'app/build/',
    'backups/',
    'web_editor/'
)

$files = @(
    Get-ChildItem -LiteralPath $rootFull -Recurse -File -Force | Where-Object {
        $relative = $_.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
        -not ($excludedPrefixes | Where-Object {
            $relative.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase)
        })
    } | Sort-Object FullName
)

$stream = [System.IO.File]::Open(
    $zipPath,
    [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::Write
)

try {
    $archive = [System.IO.Compression.ZipArchive]::new(
        $stream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        foreach ($file in $files) {
            $relative = $file.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
            $entry = $archive.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
            $entryStream = $entry.Open()
            $sourceStream = [System.IO.File]::OpenRead($file.FullName)
            try {
                $sourceStream.CopyTo($entryStream)
            } finally {
                $sourceStream.Dispose()
                $entryStream.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    $stream.Dispose()
}

$info = Get-Item -LiteralPath $zipPath
$bad = @(
    $files | Where-Object {
        $relative = $_.FullName.Substring($rootFull.Length + 1).Replace('\', '/')
        $relative.StartsWith('app/build/', [System.StringComparison]::OrdinalIgnoreCase) -or
        $relative.StartsWith('.git/', [System.StringComparison]::OrdinalIgnoreCase) -or
        $relative.StartsWith('.gradle/', [System.StringComparison]::OrdinalIgnoreCase) -or
        $relative.StartsWith('build/', [System.StringComparison]::OrdinalIgnoreCase) -or
        $relative.StartsWith('backups/', [System.StringComparison]::OrdinalIgnoreCase) -or
        $relative.StartsWith('web_editor/', [System.StringComparison]::OrdinalIgnoreCase)
    }
)
if ($bad.Count -ne 0) {
    throw "Backup contains excluded paths: $($bad.Count)"
}

Write-Host "BACKUP $($info.FullName)"
Write-Host "FILES $($files.Count)"
Write-Host "SIZE $([math]::Round($info.Length / 1MB, 1)) MB"
