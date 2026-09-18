$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$AppManifestPath = Join-Path $Root 'app/src/main/AndroidManifest.xml'
$ManifestPaths = Get-ChildItem -Path $Root -Recurse -Filter AndroidManifest.xml -File |
    Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]AndroidManifest\.xml$' -and $_.FullName -notmatch '[\\/]build[\\/]' }

$ExportedCount = 0
foreach ($ManifestPath in $ManifestPaths) {
    $Manifest = Get-Content -Raw $ManifestPath.FullName
    foreach ($Forbidden in @('MANAGE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'android.permission.INTERNET')) {
        if ($Manifest.Contains($Forbidden)) {
            $Relative = [System.IO.Path]::GetRelativePath($Root, $ManifestPath.FullName)
            throw "Forbidden Phase-0 permission in ${Relative}: $Forbidden"
        }
    }
    $ExportedCount += ([regex]::Matches($Manifest, 'android:exported="true"')).Count
}

if ($ExportedCount -ne 1) { throw "Expected exactly one exported=true component across main manifests; found $ExportedCount." }
$AppManifest = Get-Content -Raw $AppManifestPath
if (-not $AppManifest.Contains('android:name=".MainActivity"') -or -not $AppManifest.Contains('android:exported="true"')) {
    throw 'The only exported component must be the launcher MainActivity.'
}
if (-not $AppManifest.Contains('android:allowBackup="false"')) { throw 'allowBackup must be false during bootstrap.' }
if (-not $AppManifest.Contains('android:usesCleartextTraffic="false"')) { throw 'Cleartext traffic must be disabled.' }
Write-Host 'Security baseline static checks passed.'
