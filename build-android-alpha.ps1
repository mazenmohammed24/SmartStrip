param(
    [string]$OutputDir = "D:\FTP\Android"
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$androidDir = Join-Path $repoRoot "android"
$apkSource = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"

$jdkHome = Join-Path $env:USERPROFILE "jdk17\jdk-17.0.11+9"
$androidHome = Join-Path $env:USERPROFILE "android-sdk"
if (Test-Path $jdkHome) {
    $env:JAVA_HOME = $jdkHome
    $env:PATH = "$jdkHome\bin;$env:PATH"
}
if (Test-Path $androidHome) {
    $env:ANDROID_HOME = $androidHome
    $env:ANDROID_SDK_ROOT = $androidHome
    $env:PATH = "$androidHome\platform-tools;$env:PATH"
}

New-Item -ItemType Directory -Force $OutputDir | Out-Null

Push-Location $androidDir
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$gradleFile = Join-Path $androidDir "app\build.gradle.kts"
$gradleText = Get-Content $gradleFile -Raw
$versionName = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $versionName) { $versionName = "0.0" }
$alphaVersion = if ($versionName.EndsWith("-alpha")) { $versionName } else { "$versionName-alpha" }
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $OutputDir "SmartStrip-$alphaVersion-$stamp.apk"

Copy-Item $apkSource $dest -Force
Write-Host "Alpha APK copied to $dest"
