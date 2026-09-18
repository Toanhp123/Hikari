$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $Root 'gradlew.bat') -p $Root verifyFast --no-daemon @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
