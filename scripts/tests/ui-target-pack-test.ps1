$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$renderer = Join-Path $repoRoot 'tools/ui-target/render-ui-target.ps1'

if (-not (Test-Path -LiteralPath $renderer)) {
    throw "Renderer not found: $renderer"
}

$required = @(
    '00-overview-compact.png',
    '01-compact-360x800/01-discover.png',
    '01-compact-360x800/02-home.png',
    '01-compact-360x800/03-library.png',
    '01-compact-360x800/04-search.png',
    '01-compact-360x800/05-story-overview.png',
    '01-compact-360x800/06-story-sources.png',
    '01-compact-360x800/07-story-chapters.png',
    '01-compact-360x800/08-mapping.png',
    '01-compact-360x800/09-downloads.png',
    '01-compact-360x800/10-updates.png',
    '01-compact-360x800/11-reader.png',
    '01-compact-360x800/12-plugin-manager.png',
    '01-compact-360x800/13-settings.png',
    '02-large-phone-412x892/01-discover.png',
    '02-large-phone-412x892/02-home.png',
    '02-large-phone-412x892/03-library.png',
    '02-large-phone-412x892/04-story.png',
    '03-medium-600x960/01-discover.png',
    '03-medium-600x960/02-home.png',
    '03-medium-600x960/03-library.png',
    '03-medium-600x960/04-story.png',
    '04-ux-states-360x800/01-loading.png',
    '04-ux-states-360x800/02-empty.png',
    '04-ux-states-360x800/03-error.png',
    '04-ux-states-360x800/04-partial-failure.png',
    '04-ux-states-360x800/05-offline.png',
    '05-light-360x800/01-discover.png',
    '05-light-360x800/02-home.png',
    '05-light-360x800/03-library.png',
    '05-light-360x800/04-story.png',
    '05-light-360x800/05-plugin-manager.png',
    '05-light-360x800/06-settings.png',
    '05-light-360x800/07-reader.png'
)


function Assert-PngHasVisualContent {
    param([Parameter(Mandatory = $true)][string]$Path)

    $bitmap = New-Object System.Drawing.Bitmap -ArgumentList $Path
    try {
        $uniqueColors = [System.Collections.Generic.HashSet[int]]::new()
        $columns = 12
        $rows = 16

        for ($row = 0; $row -lt $rows; $row++) {
            $y = [Math]::Min($bitmap.Height - 1, [int][Math]::Floor((($row + 0.5) / $rows) * $bitmap.Height))
            for ($column = 0; $column -lt $columns; $column++) {
                $x = [Math]::Min($bitmap.Width - 1, [int][Math]::Floor((($column + 0.5) / $columns) * $bitmap.Width))
                [void]$uniqueColors.Add($bitmap.GetPixel($x, $y).ToArgb())
            }
        }

        # A blank renderer output is uniformly painted with only the page
        # background. Keep this assertion focused on that regression instead of
        # rejecting intentionally low-contrast UI targets.
        if ($uniqueColors.Count -lt 2) {
            throw "PNG is uniform and is likely a blank renderer output: $Path (sampled colors: $($uniqueColors.Count))."
        }
    }
    finally {
        $bitmap.Dispose()
    }
}

function Get-PngDimensions {
    param([Parameter(Mandatory = $true)][string]$Path)

    [byte[]]$bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 24) {
        throw "PNG is too small to contain an IHDR chunk: $Path"
    }

    [byte[]]$signature = @(137, 80, 78, 71, 13, 10, 26, 10)
    for ($index = 0; $index -lt $signature.Length; $index++) {
        if ($bytes[$index] -ne $signature[$index]) {
            throw "Not a PNG file: $Path"
        }
    }

    $width = ($bytes[16] * 16777216) + ($bytes[17] * 65536) + ($bytes[18] * 256) + $bytes[19]
    $height = ($bytes[20] * 16777216) + ($bytes[21] * 65536) + ($bytes[22] * 256) + $bytes[23]

    return @($width, $height)
}

$outputDir = Join-Path ([System.IO.Path]::GetTempPath()) ("hikari-ui-target-test-" + [System.Guid]::NewGuid().ToString('N'))

try {
    & $renderer -OutputDirectory $outputDir

    foreach ($relativePath in $required) {
        $actualPath = Join-Path $outputDir $relativePath
        if (-not (Test-Path -LiteralPath $actualPath)) {
            throw "Missing required target-pack image: $relativePath"
        }

        Assert-PngHasVisualContent -Path $actualPath

        if ($relativePath -match '^[^/]+-(?<width>\d+)x(?<height>\d+)/') {
            $dimensions = Get-PngDimensions -Path $actualPath
            $expectedWidth = [int]$Matches['width'] * 2
            $expectedHeight = [int]$Matches['height'] * 2

            if (($dimensions[0] -ne $expectedWidth) -or ($dimensions[1] -ne $expectedHeight)) {
                throw "Unexpected PNG dimensions for ${relativePath}: $($dimensions[0])x$($dimensions[1]); expected ${expectedWidth}x${expectedHeight}."
            }
        }
    }

    Write-Host "UI target pack verified: $($required.Count) required PNG files with deterministic 2x dimensions and non-uniform visual content."
}
finally {
    if (Test-Path -LiteralPath $outputDir) {
        Remove-Item -LiteralPath $outputDir -Recurse -Force
    }
}
