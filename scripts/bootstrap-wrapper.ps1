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

$GradleVersion = '9.4.1'
$DistributionSha = '2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
$WrapperSha = '55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Temp = Join-Path ([System.IO.Path]::GetTempPath()) ("universalmedia-gradle-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $Temp | Out-Null
try {
    $JavaVersionResult = Get-JavaVersionResult
    if ($JavaVersionResult.ExitCode -ne 0) {
        throw "JDK 17 is required; java -version exited with code $($JavaVersionResult.ExitCode)."
    }
    $JavaVersion = $JavaVersionResult.Text
    if ($JavaVersion -notmatch 'version "17(?:\.|\")') {
        throw "JDK 17 is required. Found: $JavaVersion"
    }
    $Zip = Join-Path $Temp "gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $Zip
    if ((Get-FileHash -Algorithm SHA256 $Zip).Hash.ToLowerInvariant() -ne $DistributionSha) {
        throw 'Gradle distribution SHA-256 mismatch.'
    }
    Expand-Archive -Path $Zip -DestinationPath $Temp

    $Generator = Join-Path $Temp 'wrapper-project'
    New-Item -ItemType Directory -Path $Generator | Out-Null
    Set-Content -Path (Join-Path $Generator 'settings.gradle.kts') -Value 'rootProject.name = "wrapper-bootstrap"'
    & (Join-Path $Temp "gradle-$GradleVersion/bin/gradle.bat") -p $Generator wrapper --gradle-version $GradleVersion --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed with exit code $LASTEXITCODE" }

    Copy-Item (Join-Path $Generator 'gradlew') (Join-Path $Root 'gradlew') -Force
    Copy-Item (Join-Path $Generator 'gradlew.bat') (Join-Path $Root 'gradlew.bat') -Force
    Copy-Item (Join-Path $Generator 'gradle/wrapper/gradle-wrapper.jar') (Join-Path $Root 'gradle/wrapper/gradle-wrapper.jar') -Force
    $Properties = Join-Path $Root 'gradle/wrapper/gradle-wrapper.properties'
    Copy-Item (Join-Path $Generator 'gradle/wrapper/gradle-wrapper.properties') $Properties -Force
    if (-not (Select-String -Quiet -Path $Properties -Pattern '^distributionSha256Sum=')) {
        Add-Content -Path $Properties -Value "distributionSha256Sum=$DistributionSha"
    }
    if ((Get-FileHash -Algorithm SHA256 (Join-Path $Root 'gradle/wrapper/gradle-wrapper.jar')).Hash.ToLowerInvariant() -ne $WrapperSha) {
        throw 'Gradle wrapper JAR SHA-256 mismatch.'
    }
    $PropsText = Get-Content -Raw $Properties
    if (-not $PropsText.Contains("gradle-$GradleVersion-bin.zip")) { throw 'Generated wrapper version mismatch.' }
    if (-not $PropsText.Contains("distributionSha256Sum=$DistributionSha")) { throw 'Distribution SHA pin missing.' }
    Write-Host "Official Gradle $GradleVersion wrapper generated and verified."
}
finally {
    Remove-Item -Recurse -Force $Temp -ErrorAction SilentlyContinue
}
