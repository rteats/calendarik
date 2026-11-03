param([string]$Serial)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$previousSerial = $env:ANDROID_SERIAL
Push-Location $projectDir
try {
    if ($Serial) { $env:ANDROID_SERIAL = $Serial }
    $sdkLine = Get-Content -LiteralPath local.properties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
    if (-not $sdkLine) { throw 'Open the project in Android Studio to configure the Android SDK first.' }
    $sdkDir = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
    $adbPath = Join-Path $sdkDir 'platform-tools/adb.exe'
    & $adbPath get-state
    if ($LASTEXITCODE -ne 0) { throw 'Connect one phone, or pass -Serial for the intended phone.' }
    & .\gradlew.bat :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; installation stopped.' }
    & $adbPath install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Update install failed; keep the installed app and its data.' }
    & $adbPath shell am start -W -n com.rteats.calendarik/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
} finally {
    Pop-Location
    $env:ANDROID_SERIAL = $previousSerial
}
