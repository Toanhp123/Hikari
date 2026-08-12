param(
    [string]$Output = 'E:\Downloads\Hikari-UI-Target-Pack.zip',
    [string]$EdgePath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$renderer = Join-Path $PSScriptRoot 'render-ui-target.ps1'
$buildDirectory = Join-Path $PSScriptRoot 'build'

if (Test-Path -LiteralPath $buildDirectory) {
    Remove-Item -LiteralPath $buildDirectory -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $buildDirectory | Out-Null

if ($EdgePath) {
    & $renderer -OutputDirectory $buildDirectory -EdgePath $EdgePath
}
else {
    & $renderer -OutputDirectory $buildDirectory
}

$readme = @'
# Hikari UI Target Pack

This pack is generated from `tools/ui-target/src` with deterministic mock data and StoryId-derived abstract SVG artwork. It consumes no production code and does not fetch remote assets.

## Approved product rules

- Top-level navigation is **Discover / Home / Library** only.
- Search, Story, Sources, Chapters, mapping, Downloads, Updates, Reader, Plugins, and Settings are focused destinations and do not show the floating top-level navigation.
- Artwork leads. Covers and backdrops share deterministic StoryId-derived visual identity; missing real artwork must preserve geometry with a stable fallback.
- Glass is selective: search, utility surfaces, floating navigation, sheets, and Reader chrome. Content cards and cover artwork are not individually blurred.
- API 31+ uses bounded blur. API 26-30 uses the same geometry with a readable translucent fallback.
- Compact `360x800dp`, large-phone `412x892dp`, and medium `600x960dp` targets are independent responsive layouts, exported at 2x pixels.
- The layout follows a 4dp grid and 16dp baseline screen padding.
- Cached usable content stays visible during refresh, partial source failure, and offline states where supported.
- Do not fabricate recommendations, social activity, friends, trailers, remote tracking, marketplace, or cloud-account flows.
- Plugins is a **Wave 11 visual target** in this pack, not a current clickable route.
- Settings is a **Wave 10 visual target** in this pack, not a current clickable route.
- Current checkpoint utility presentation may expose Downloads and Updates only where the existing domain supports them.

## Render matrix

- `01-compact-360x800`: full critical flow in dark theme.
- `02-large-phone-412x892`: Discover, Home, Library, Story.
- `03-medium-600x960`: responsive Discover, Home, Library, Story.
- `04-ux-states-360x800`: loading, empty, full error, partial failure, offline.
- `05-light-360x800`: light-theme Discover, Home, Library, Story, Plugin target, Settings target, Reader.

The renderer uses Microsoft Edge headless with `--force-device-scale-factor=2`, hides scrollbars, and waits on a fixed virtual-time budget before capture.
'@

$readmePath = Join-Path $buildDirectory 'README.md'
Set-Content -LiteralPath $readmePath -Value $readme -Encoding UTF8

$outputPath = [System.IO.Path]::GetFullPath($Output)
$outputParent = Split-Path -Parent $outputPath
if ($outputParent -and -not (Test-Path -LiteralPath $outputParent)) {
    New-Item -ItemType Directory -Force -Path $outputParent | Out-Null
}
if (Test-Path -LiteralPath $outputPath) {
    Remove-Item -LiteralPath $outputPath -Force
}

Compress-Archive -Path (Join-Path $buildDirectory '*') -DestinationPath $outputPath -CompressionLevel Optimal
Write-Host "Hikari UI target pack created: $outputPath"
