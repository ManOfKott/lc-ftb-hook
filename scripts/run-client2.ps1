# Zweiter Dev-Client (eigenes Game-Verzeichnis, Username DevPlayer2)
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
Write-Host "Starte zweiten Minecraft Dev-Client (DevPlayer2)..." -ForegroundColor Green
Write-Host "Server zuerst starten, dann Client 1 (run-client.ps1), dann hier verbinden: Multiplayer -> Direct Connect -> localhost" -ForegroundColor Yellow

& (Join-Path $PSScriptRoot "Apply-DevClientConfig.ps1")

& .\gradlew.bat runClient2
