# Windows PowerShell: compile a debug APK.  .\scripts\build.ps1   (or .\scripts\build.ps1 release)
param([string]$Variant = "debug")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if ($Variant -eq "release") { .\gradlew.bat assembleRelease; Write-Host "APK: app\build\outputs\apk\release\app-release.apk" }
else { .\gradlew.bat assembleDebug; Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk" }
