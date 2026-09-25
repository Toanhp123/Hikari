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

Invoke-Step 'Resolve dependencies' { fvm flutter pub get }
Invoke-Step 'Check formatting' { fvm dart format -o none --set-exit-if-changed . }
Invoke-Step 'Analyze' { fvm flutter analyze }
Invoke-Step 'Run tests' { fvm flutter test }
Invoke-Step 'Check unstaged diff whitespace' { git diff --check }
Invoke-Step 'Check staged diff whitespace' { git diff --cached --check }

Write-Host "`nFoundation checks passed."
