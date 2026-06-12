# Wendet Dev-Server-Einstellungen an: Creative + OP fuer Dev und DevPlayer2
param(
    [string]$RunDir = (Join-Path (Split-Path $PSScriptRoot -Parent) "run")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $RunDir)) {
    New-Item -ItemType Directory -Path $RunDir | Out-Null
}

Copy-Item (Join-Path $PSScriptRoot "dev/ops.json") (Join-Path $RunDir "ops.json") -Force

$ftbChunksConfig = Join-Path $PSScriptRoot "dev/ftbchunks-world.snbt"
$serverConfigDir = Join-Path $RunDir "world/serverconfig"
if (Test-Path $ftbChunksConfig) {
    New-Item -ItemType Directory -Path $serverConfigDir -Force | Out-Null
    Copy-Item $ftbChunksConfig (Join-Path $serverConfigDir "ftbchunks-world.snbt") -Force
}

$propsPath = Join-Path $RunDir "server.properties"
if (-not (Test-Path $propsPath)) {
    Write-Host "server.properties noch nicht vorhanden (wird beim ersten Serverstart erzeugt)." -ForegroundColor Yellow
    return
}

$lines = Get-Content $propsPath
$overrides = @{
    "gamemode" = "creative"
    "force-gamemode" = "true"
}
$seen = @{}

$updated = foreach ($line in $lines) {
    if ($line -match '^(?<key>[^#=]+)=') {
        $key = $Matches.key.Trim()
        if ($overrides.ContainsKey($key)) {
            $seen[$key] = $true
            "$key=$($overrides[$key])"
            continue
        }
    }
    $line
}

foreach ($entry in $overrides.GetEnumerator()) {
    if (-not $seen.ContainsKey($entry.Key)) {
        $updated += "$($entry.Key)=$($entry.Value)"
    }
}

Set-Content -Path $propsPath -Value $updated -Encoding utf8
Write-Host "Dev-Server: gamemode=creative, force-gamemode=true, OP fuer Dev + DevPlayer2, ftbchunks-world.snbt" -ForegroundColor Cyan
