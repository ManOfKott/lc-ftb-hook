# Dev-Server mit JDK 21 starten
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$jdk21 = Get-ChildItem "C:\Program Files\Microsoft\jdk-21*" -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jdk21) {
    Write-Host "JDK 21 nicht gefunden. Installiere mit: winget install Microsoft.OpenJDK.21" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk21
$env:PATH = "$jdk21\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
java -version
Write-Host ""
& (Join-Path $PSScriptRoot "Apply-DevServerConfig.ps1")
Write-Host ""
Write-Host "Starte NeoForge Dev-Server..." -ForegroundColor Green
Write-Host "Hinweis: Gradle bleibt bei ~88% EXECUTING solange der Server laeuft - das ist normal." -ForegroundColor DarkGray
& .\gradlew.bat runServer
