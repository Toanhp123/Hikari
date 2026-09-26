$ErrorActionPreference = 'Stop'

if (-not (Get-Command fvm -ErrorAction SilentlyContinue)) {
    Write-Host 'ERROR: FVM was not found in PATH. Install FVM and reopen the terminal before running this check.'
    exit 1
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command
    )

    Write-Host "`n==> $Name"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

Invoke-Step 'Resolve locked dependencies' { fvm flutter pub get --enforce-lockfile }
Invoke-Step 'Check formatting' {
    $dartFiles = @(git ls-files -- '*.dart')
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not enumerate tracked Dart files.'
    }
    if ($dartFiles.Count -eq 0) {
        Write-Host 'No tracked Dart files found.'
        return
    }

    fvm dart format -o none --set-exit-if-changed -- $dartFiles
}
Invoke-Step 'Analyze' { fvm flutter analyze }
Invoke-Step 'Run tests and architecture guard' { fvm flutter test }
Invoke-Step 'Check unstaged diff whitespace' { git diff --check }
Invoke-Step 'Check staged diff whitespace' { git diff --cached --check }

Write-Host "`nFoundation checks passed."
