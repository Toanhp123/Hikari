param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build'),
    [string]$EdgePath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$edgeCandidates = @(
    'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    'C:\Program Files\Microsoft\Edge\Application\msedge.exe'
)

function Resolve-EdgeExecutable {
    if ($EdgePath) {
        if (-not (Test-Path -LiteralPath $EdgePath)) {
            throw "Edge executable was not found at the supplied path: $EdgePath"
        }
        return (Resolve-Path -LiteralPath $EdgePath).Path
    }

    if ($env:EDGE_PATH -and (Test-Path -LiteralPath $env:EDGE_PATH)) {
        return (Resolve-Path -LiteralPath $env:EDGE_PATH).Path
    }

    foreach ($candidate in $edgeCandidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    foreach ($commandName in @('msedge', 'microsoft-edge', 'chromium', 'chromium-browser')) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "Microsoft Edge was not found. Install Edge in a standard location or pass -EdgePath / set EDGE_PATH."
}


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

        # The renderer failure this guard protects against produces a truly uniform
        # screenshot (only the page background is painted). Do not require an
        # arbitrary number of colors: valid low-contrast targets such as light
        # Settings intentionally use a restrained palette.
        if ($uniqueColors.Count -lt 2) {
            throw "Screenshot is uniform and is likely a blank renderer output: $Path (sampled colors: $($uniqueColors.Count))."
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
        throw "Screenshot is not a valid PNG: $Path"
    }
    $width = ($bytes[16] * 16777216) + ($bytes[17] * 65536) + ($bytes[18] * 256) + $bytes[19]
    $height = ($bytes[20] * 16777216) + ($bytes[21] * 65536) + ($bytes[22] * 256) + $bytes[23]
    return @($width, $height)
}

$matrix = @(
    @{ Path = '00-overview-compact.png'; Screen = 'overview'; Theme = 'dark'; Width = 1200; Height = 900 },

    @{ Path = '01-compact-360x800/01-discover.png'; Screen = 'discover'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/02-home.png'; Screen = 'home'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/03-library.png'; Screen = 'library'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/04-search.png'; Screen = 'search'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/05-story-overview.png'; Screen = 'storyOverview'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/06-story-sources.png'; Screen = 'storySources'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/07-story-chapters.png'; Screen = 'storyChapters'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/08-mapping.png'; Screen = 'mapping'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/09-downloads.png'; Screen = 'downloads'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/10-updates.png'; Screen = 'updates'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/11-reader.png'; Screen = 'reader'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/12-plugin-manager.png'; Screen = 'pluginManager'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '01-compact-360x800/13-settings.png'; Screen = 'settings'; Theme = 'dark'; Width = 360; Height = 800 },

    @{ Path = '02-large-phone-412x892/01-discover.png'; Screen = 'discover'; Theme = 'dark'; Width = 412; Height = 892 },
    @{ Path = '02-large-phone-412x892/02-home.png'; Screen = 'home'; Theme = 'dark'; Width = 412; Height = 892 },
    @{ Path = '02-large-phone-412x892/03-library.png'; Screen = 'library'; Theme = 'dark'; Width = 412; Height = 892 },
    @{ Path = '02-large-phone-412x892/04-story.png'; Screen = 'storyOverview'; Theme = 'dark'; Width = 412; Height = 892 },

    @{ Path = '03-medium-600x960/01-discover.png'; Screen = 'discover'; Theme = 'dark'; Width = 600; Height = 960 },
    @{ Path = '03-medium-600x960/02-home.png'; Screen = 'home'; Theme = 'dark'; Width = 600; Height = 960 },
    @{ Path = '03-medium-600x960/03-library.png'; Screen = 'library'; Theme = 'dark'; Width = 600; Height = 960 },
    @{ Path = '03-medium-600x960/04-story.png'; Screen = 'storyOverview'; Theme = 'dark'; Width = 600; Height = 960 },

    @{ Path = '04-ux-states-360x800/01-loading.png'; Screen = 'stateLoading'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '04-ux-states-360x800/02-empty.png'; Screen = 'stateEmpty'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '04-ux-states-360x800/03-error.png'; Screen = 'stateError'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '04-ux-states-360x800/04-partial-failure.png'; Screen = 'statePartialFailure'; Theme = 'dark'; Width = 360; Height = 800 },
    @{ Path = '04-ux-states-360x800/05-offline.png'; Screen = 'stateOffline'; Theme = 'dark'; Width = 360; Height = 800 },

    @{ Path = '05-light-360x800/01-discover.png'; Screen = 'discover'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/02-home.png'; Screen = 'home'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/03-library.png'; Screen = 'library'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/04-story.png'; Screen = 'storyOverview'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/05-plugin-manager.png'; Screen = 'pluginManager'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/06-settings.png'; Screen = 'settings'; Theme = 'light'; Width = 360; Height = 800 },
    @{ Path = '05-light-360x800/07-reader.png'; Screen = 'reader'; Theme = 'light'; Width = 360; Height = 800 }
)

$resolvedEdge = Resolve-EdgeExecutable
$indexPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'src/index.html')).Path
$indexUri = ([System.Uri]::new($indexPath)).AbsoluteUri
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$profileDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("hikari-ui-target-edge-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $profileDirectory | Out-Null

try {
    foreach ($target in $matrix) {
        $relativePath = $target.Path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $screenshotPath = Join-Path $outputRoot $relativePath
        $parent = Split-Path -Parent $screenshotPath
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
        if (Test-Path -LiteralPath $screenshotPath) {
            Remove-Item -LiteralPath $screenshotPath -Force
        }

        $query = "screen=$($target.Screen)&theme=$($target.Theme)&width=$($target.Width)&height=$($target.Height)"
        $url = "$indexUri`?$query"
        $arguments = @(
            '--headless=new',
            '--disable-gpu',
            '--hide-scrollbars',
            '--no-first-run',
            '--disable-first-run-ui',
            '--allow-file-access-from-files',
            '--run-all-compositor-stages-before-draw',
            '--force-device-scale-factor=2',
            "--window-size=$($target.Width),$($target.Height)",
            '--virtual-time-budget=1500',
            "--user-data-dir=$profileDirectory",
            "--screenshot=$screenshotPath",
            $url
        )

        & $resolvedEdge @arguments | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Edge failed while rendering $($target.Path) with exit code $LASTEXITCODE."
        }
        if (-not (Test-Path -LiteralPath $screenshotPath)) {
            throw "Edge did not produce the expected screenshot: $screenshotPath"
        }

        $dimensions = Get-PngDimensions -Path $screenshotPath
        $expectedWidth = [int]$target.Width * 2
        $expectedHeight = [int]$target.Height * 2
        if (($dimensions[0] -ne $expectedWidth) -or ($dimensions[1] -ne $expectedHeight)) {
            throw "Unexpected screenshot size for $($target.Path): $($dimensions[0])x$($dimensions[1]); expected ${expectedWidth}x${expectedHeight}."
        }

        Assert-PngHasVisualContent -Path $screenshotPath
        Write-Host "Rendered $($target.Path) [$($target.Theme)] ${expectedWidth}x${expectedHeight}"
    }
}
finally {
    if (Test-Path -LiteralPath $profileDirectory) {
        Remove-Item -LiteralPath $profileDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Hikari UI target rendering complete: $($matrix.Count) deterministic PNG files in $outputRoot"
