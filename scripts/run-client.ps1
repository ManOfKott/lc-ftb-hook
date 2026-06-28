# Dev-Client mit JDK 21 starten
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
Write-Host "Starte Minecraft Dev-Client (beim ersten Mal kann das einige Minuten dauern)..." -ForegroundColor Green
Write-Host "Hinweis: Gradle bleibt bei ~88% EXECUTING solange der Client laeuft - das ist normal." -ForegroundColor DarkGray
Write-Host "Server zuerst starten, dann im Client: Multiplayer -> Direct Connect -> localhost" -ForegroundColor Yellow

# Verhindert Auto-Reconnect zum letzten (externen) Server aus servers.dat.
$serversDat = Join-Path (Split-Path $PSScriptRoot -Parent) "run/servers.dat"
if (Test-Path $serversDat) {
    Remove-Item $serversDat -Force
}

& (Join-Path $PSScriptRoot "Apply-DevClientConfig.ps1")

& .\gradlew.bat runClient
