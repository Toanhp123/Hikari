param(
    [switch]$DoctorOnly,
    [switch]$HostOnly
)

$ErrorActionPreference = 'Stop'
function Get-JavaVersionResult {
    $StartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $StartInfo.FileName = 'java'
    $StartInfo.Arguments = '-version'
    $StartInfo.UseShellExecute = $false
    $StartInfo.RedirectStandardOutput = $true
    $StartInfo.RedirectStandardError = $true

    $Process = New-Object System.Diagnostics.Process
    $Process.StartInfo = $StartInfo
    try {
        if (-not $Process.Start()) {
            throw 'Failed to start java -version.'
        }
        $StdOut = $Process.StandardOutput.ReadToEnd()
        $StdErr = $Process.StandardError.ReadToEnd()
        $Process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $Process.ExitCode
            Text = ($StdOut + [Environment]::NewLine + $StdErr).Trim()
        }
    } finally {
        $Process.Dispose()
    }
}

if ($DoctorOnly -and $HostOnly) {
    throw '-DoctorOnly and -HostOnly are mutually exclusive.'
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ExpectedGradle = '9.4.1'
$ExpectedDistSha = '2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
$ExpectedWrapperSha = '55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c'

$JavaVersionResult = Get-JavaVersionResult
$JavaVersionText = $JavaVersionResult.Text
if ($JavaVersionResult.ExitCode -ne 0 -or $JavaVersionText -notmatch 'version "(\d+)') {
    throw 'JDK 17 is required; java -version could not be read.'
}
if ($Matches[1] -ne '17') {
    throw "Expected JDK 17, found Java $($Matches[1])."
}
Write-Host 'JDK 17: OK'

$SdkRoot = $env:ANDROID_SDK_ROOT
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_HOME }
if (-not $SdkRoot) { throw 'ANDROID_SDK_ROOT (or ANDROID_HOME) must point to an Android SDK.' }
if (-not (Test-Path (Join-Path $SdkRoot 'platforms\android-37.0\android.jar'))) {
    throw "Android SDK platform 37 not found under $SdkRoot\platforms\android-37.0"
}
if (-not (Test-Path (Join-Path $SdkRoot 'build-tools\37.0.0'))) {
    throw "Android build-tools 37.0.0 not found under $SdkRoot\build-tools\37.0.0"
}
Write-Host 'Android SDK 37: OK'

$WrapperProps = Join-Path $Root 'gradle\wrapper\gradle-wrapper.properties'
$WrapperText = Get-Content $WrapperProps -Raw
if ($WrapperText -notmatch [regex]::Escape("gradle-$ExpectedGradle-bin.zip")) {
    throw "Gradle wrapper must pin $ExpectedGradle."
}
if ($WrapperText -notmatch [regex]::Escape("distributionSha256Sum=$ExpectedDistSha")) {
    throw 'Gradle distribution SHA-256 pin is missing or incorrect.'
}
Write-Host 'Wrapper properties: OK'

$DaemonJvmProps = Join-Path $Root 'gradle\gradle-daemon-jvm.properties'
if (Test-Path $DaemonJvmProps) {
    $DaemonJvmText = Get-Content $DaemonJvmProps -Raw
    if ($DaemonJvmText -notmatch '(?m)^toolchainVersion=17\s*$') {
        $ConfiguredDaemonJvm = if ($DaemonJvmText -match '(?m)^toolchainVersion=(.+)$') { $Matches[1].Trim() } else { 'unset' }
        throw "Gradle daemon JVM criteria must target JDK 17, found $ConfiguredDaemonJvm."
    }
    Write-Host 'Gradle daemon JVM criteria: JDK 17 OK'
}

$WrapperJar = Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar'
if (Test-Path $WrapperJar) {
    $Hash = (Get-FileHash $WrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Hash -ne $ExpectedWrapperSha) { throw 'Gradle wrapper JAR checksum mismatch.' }
    Write-Host 'Wrapper JAR: OK'
} elseif ($DoctorOnly) {
    Write-Host 'Wrapper JAR: PENDING (full run will materialize it with scripts\bootstrap-wrapper.ps1)'
} else {
    Write-Host "Wrapper JAR missing; generating the official $ExpectedGradle wrapper..."
    & (Join-Path $Root 'scripts\bootstrap-wrapper.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper bootstrap failed.' }
    $Hash = (Get-FileHash $WrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Hash -ne $ExpectedWrapperSha) { throw 'Generated Gradle wrapper JAR checksum mismatch.' }
    Write-Host 'Wrapper JAR: OK'
}

if ($DoctorOnly) {
    Write-Host 'Bootstrap environment doctor: PASS'
    exit 0
}

Push-Location $Root
try {
    Write-Host '== Gradle runtime =='
    & .\gradlew.bat --version --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle --version failed.' }

    Write-Host '== Gradle configuration/help =='
    & .\gradlew.bat help --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle help failed.' }

    Write-Host '== Fast quality gate =='
    & .\gradlew.bat verifyFast --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'verifyFast failed.' }

    if (-not $HostOnly) {
        $Adb = Get-Command adb -ErrorAction SilentlyContinue
        if ($Adb) {
            $AdbPath = $Adb.Source
        } else {
            $AdbPath = Join-Path $SdkRoot 'platform-tools\adb.exe'
            if (-not (Test-Path $AdbPath)) { throw 'adb is required for the full bootstrap gate; use -HostOnly to skip device smoke.' }
        }

        if ($env:ANDROID_SERIAL) {
            $State = (& $AdbPath -s $env:ANDROID_SERIAL get-state 2>$null | Out-String).Trim()
            if ($State -ne 'device') { throw "ANDROID_SERIAL=$($env:ANDROID_SERIAL) is not an online device." }
        } else {
            $Online = @(& $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s*$' })
            if ($Online.Count -ne 1) { throw "Expected exactly one online adb device when ANDROID_SERIAL is unset; found $($Online.Count)." }
        }

        Write-Host '== Android launch smoke =='
        & .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Android launch smoke failed.' }
    }

    Write-Host '== Release-like gate =='
    & .\gradlew.bat verifyRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'verifyRelease failed.' }

    Write-Host 'Bootstrap execution gate: PASS'
} finally {
    Pop-Location
}
