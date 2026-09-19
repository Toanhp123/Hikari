$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$TargetPackage = 'app.universalmedia.debug'
$TestClass = 'app.universalmedia.ProcessDeathSafEndToEndTest'
$FixtureSource = Join-Path $Root 'playback\media3\src\androidTest\assets\fixture.mp4'
$DeviceFolder = '/sdcard/Download/HikariTask11'
$DeviceFixture = "$DeviceFolder/task11-fixture.mp4"
$TargetApk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
$TestApk = Join-Path $Root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'

$AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($AdbCommand) {
    $Adb = $AdbCommand.Source
} else {
    $SdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_HOME }
    if (-not $SdkRoot) { throw 'adb not found and ANDROID_SDK_ROOT/ANDROID_HOME is unset.' }
    $Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path $Adb)) { throw "adb not found at $Adb" }
}

$AdbPrefix = @()
if ($env:ANDROID_SERIAL) { $AdbPrefix = @('-s', $env:ANDROID_SERIAL) }

function Invoke-Adb {
    param([string[]]$CommandArgs)
    & $Adb @AdbPrefix @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed ($LASTEXITCODE): $($CommandArgs -join ' ')"
    }
}

function Invoke-Phase {
    param([string]$Method, [string]$Instrumentation)
    $Args = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', "$TestClass#$Method",
        $Instrumentation
    )
    $Output = (& $Adb @AdbPrefix @Args 2>&1 | Out-String)
    $ExitCode = $LASTEXITCODE
    Write-Host $Output.TrimEnd()
    if ($ExitCode -ne 0) { throw "Instrumentation transport failed for $Method." }
    if ($Output -notmatch 'OK \([0-9]+ tests?\)') {
        throw "Instrumentation did not report JUnit success for $Method."
    }
    if ($Output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED') {
        throw "Instrumentation reported failure for $Method."
    }
}

Push-Location $Root
try {
    Invoke-Adb @('get-state')
    if (-not (Test-Path $FixtureSource)) { throw "Missing self-owned playback fixture: $FixtureSource" }

    Write-Host '== Task 11: build target and instrumentation APKs =='
    & (Join-Path $Root 'gradlew.bat') ':app:assembleDebug' ':app:assembleDebugAndroidTest' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw 'Task 11 APK build failed.' }
    if (-not (Test-Path $TargetApk) -or -not (Test-Path $TestApk)) {
        throw 'Expected debug APK outputs were not produced.'
    }

    Write-Host '== Task 11: install once and prepare clean device state =='
    Invoke-Adb @('install', '-r', '-t', $TargetApk)
    Invoke-Adb @('install', '-r', '-t', $TestApk)

    $InstrumentationLines = (& $Adb @AdbPrefix 'shell' 'pm' 'list' 'instrumentation' | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Could not list instrumentation components.' }
    $Pattern = "(?m)^instrumentation:(\S+) \(target=$([regex]::Escape($TargetPackage))\)\s*$"
    $Match = [regex]::Match($InstrumentationLines, $Pattern)
    if (-not $Match.Success) { throw "Could not resolve instrumentation component for $TargetPackage." }
    $Instrumentation = $Match.Groups[1].Value.Trim()
    $TestPackage = $Instrumentation.Split('/')[0]

    Invoke-Adb @('shell', 'pm', 'clear', $TargetPackage)
    Invoke-Adb @('shell', 'pm', 'clear', $TestPackage)
    Invoke-Adb @('shell', 'rm', '-rf', $DeviceFolder)
    Invoke-Adb @('shell', 'mkdir', '-p', $DeviceFolder)
    Invoke-Adb @('push', $FixtureSource, $DeviceFixture)

    Write-Host ''
    Write-Host '== Phase A: establish real SAF + Room + Library + Media3 state =='
    Write-Host 'When DocumentsUI opens, choose Internal/shared storage > Download > HikariTask11,'
    Write-Host 'then confirm Use this folder / Allow. Do not choose the parent Download folder.'
    Invoke-Phase -Method 'phaseA_establishDurableState' -Instrumentation $Instrumentation

    Write-Host ''
    Write-Host '== External real process stop =='
    Invoke-Adb @('shell', 'am', 'force-stop', $TargetPackage)
    Start-Sleep -Seconds 1

    Write-Host ''
    Write-Host '== Phase B: fresh process reconstructs durable state =='
    Invoke-Phase -Method 'phaseB_reconstructAfterForceStop' -Instrumentation $Instrumentation

    Write-Host ''
    Write-Host 'Task 11 process-death / persisted-SAF device evidence: PASS'
} finally {
    Pop-Location
}
