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
    $dartFiles = @(
        git ls-files --cached --others --exclude-standard -- '*.dart' |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not enumerate workspace Dart files.'
    }
    if ($dartFiles.Count -eq 0) {
        Write-Host 'No Dart files found.'
        return
    }

    # FVM uses a Windows command wrapper. Passing every Dart path at once can
    # exceed the Windows command-line length limit as the workspace grows.
    # Keep the same workspace-file semantics, but format-check in bounded batches.
    $maxBatchChars = 6000
    $batch = @()
    $batchChars = 0

    foreach ($dartFile in $dartFiles) {
        # Include a small allowance for quoting/separators around each argument.
        $argumentChars = $dartFile.Length + 3
        if ($batch.Count -gt 0 -and ($batchChars + $argumentChars) -gt $maxBatchChars) {
            fvm dart format -o none --set-exit-if-changed -- $batch
            if ($LASTEXITCODE -ne 0) {
                return
            }
            $batch = @()
            $batchChars = 0
        }

        $batch += $dartFile
        $batchChars += $argumentChars
    }

    if ($batch.Count -gt 0) {
        fvm dart format -o none --set-exit-if-changed -- $batch
    }
}
Invoke-Step 'Analyze' { fvm flutter analyze }
Invoke-Step 'Run tests and architecture guard' { fvm flutter test }
Invoke-Step 'Check unstaged diff whitespace' { git diff --check }
Invoke-Step 'Check staged diff whitespace' { git diff --cached --check }

Write-Host "`nFoundation checks passed."
