# Windows PowerShell: build + install + launch + logs. Run from the project folder:  .\scripts\dev.ps1
$ErrorActionPreference = "Stop"
$AppId = "com.vislupus.waifuverse"
Set-Location (Join-Path $PSScriptRoot "..")
.\gradlew.bat installDebug
adb shell am start -n "$AppId/.MainActivity" | Out-Null
adb logcat -c
Start-Sleep -Seconds 2
$procId = (adb shell pidof $AppId).Trim()
if ($procId) { adb logcat --pid=$procId } else { adb logcat "*:E" }
