# Launches the dev client (with Xaero's Minimap/World Map + the FTB Chunks
# compat addon, same as scripts\run-client.ps1) but with the Xaero-integration
# mixins (lc_ftb_hook_xaero.mixins.json) forced to STRICT mode:
# required=true, injectors.defaultRequire=1.
#
# lc_ftb_hook_xaero.mixins.json is normally soft-fail on purpose (see
# gradle.properties, xaero_mixin_required/xaero_mixin_default_require) so a
# real player whose Xaero/ftbxaerocompat update changes something internally
# just silently loses the extra menu entries instead of crashing. That also
# means a broken mixin target is otherwise invisible - nothing logs, nothing
# crashes, the feature just quietly stops working.
#
# This script trades that safety for a loud failure, so YOU catch a broken
# mixin target during testing instead of a player noticing a missing button.
# It never touches gradle.properties - the override is a one-off Gradle -P
# property for this run only.
#
# Run this whenever you bump xaeros_minimap_version, xaeros_worldmap_version,
# or ftb_xaero_compat_project/file in gradle.properties. Click through:
# right-click menu on a claimed chunk (Sell/Buy/Assign to Region), the hover
# claim-info panel, and the chunk highlight tooltip. If any of the 4 Xaero
# mixins no longer applies, the game will crash with a Mixin error naming the
# broken target instead of silently working around it.

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

Write-Host "STRICT MODE: Xaero-Mixins muessen treffen, sonst Crash mit Mixin-Fehler." -ForegroundColor Yellow
Write-Host "Zum Testen nach einem Xaero/ftbxaerocompat Versions-Bump." -ForegroundColor Yellow
Write-Host ""

# Verhindert Auto-Reconnect zum letzten (externen) Server aus servers.dat.
$serversDat = Join-Path (Split-Path $PSScriptRoot -Parent) "run/servers.dat"
if (Test-Path $serversDat) {
    Remove-Item $serversDat -Force
}

& (Join-Path $PSScriptRoot "Apply-DevClientConfig.ps1")

& .\gradlew.bat runClient -Pxaero_mixin_required=true -Pxaero_mixin_default_require=1

Write-Host ""
Write-Host "Strict-Mode-Lauf beendet - normaler Build (gradlew build) setzt die Xaero-Mixins wieder auf soft-fail zurueck." -ForegroundColor DarkGray
