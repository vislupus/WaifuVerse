# Windows PowerShell: compile a debug APK.  .\scripts\build.ps1   (or .\scripts\build.ps1 release)
param([string]$Variant = "debug")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if ($Variant -eq "release") { .\gradlew.bat assembleRelease; Get-ChildItem app\build\outputs\apk\release\*.apk | ForEach-Object { Write-Host "APK: $($_.FullName)" } }
else { .\gradlew.bat assembleDebug; Get-ChildItem app\build\outputs\apk\debug\*.apk | ForEach-Object { Write-Host "APK: $($_.FullName)" } }
