# Windows PowerShell: build + install + launch.  .\scripts\run.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
.\gradlew.bat installDebug
adb shell am start -n "com.vislupus.waifuverse/.MainActivity" | Out-Null
