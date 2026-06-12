# LC FTB Hook – 5-Minuten-Test

Kurze Checkliste zum lokalen Testen der Mod im Dev-Client.

## Einmalig vorbereiten

PowerShell im Projektordner:

```powershell
cd c:\Users\malik\Programming\06_Minecraft\lc-ftb-hook
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

JDK 21 fehlt? `winget install Microsoft.OpenJDK.21`

---

## 1. Client starten (~2 Min. beim ersten Mal)

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat runClient
```

- Neue **Einzelspieler-Welt** (Creative reicht).
- Cheats erlauben (für OP-Befehle).

---

## 2. Schnell-Config (Upkeep in 2 Minuten statt 60)

Nach dem ersten Weltstart die Datei bearbeiten:

```text
run\saves\<Weltname>\serverconfig\lc_ftb_hook-server.toml
```

Empfohlene Testwerte:

```toml
[general]
    claimPrice = 100
    forceLoadPrice = 50
    upkeepPeriodMinutes = 2

[protectionPrices]
    mobGriefProtectionPrice = 10
    explosionProtectionPrice = 10
    pvpDisablePrice = 5
    blockInteractProtectionPrice = 15
    blockEditProtectionPrice = 15
```

Welt verlassen und neu laden (oder Client neu starten), damit die Config greift.

---

## 3. Geld aufs Konto

Im Chat (du bist in Singleplayer = OP):

```text
/lcbank give players @s 5000
```

Kontostand prüfen: ATM platzieren und öffnen, oder Wallet/ATM der Mod.

---

## 4. Test-Checkliste

### A – Chunk claimen kostet Geld

1. `M` drücken → **Claimed Chunks** (Karten-Icon).
2. Ein Chunk claimen.
3. **Erwartung:** Claim klappt; am Konto sind **100** Kupfer-Einheiten weniger (`claimPrice`).
4. `/lcbank give players @s 0` oder alles auszahlen, dann erneut claimen.
5. **Erwartung:** Claim wird abgelehnt; Chat: *"Not enough money in the bank account..."*

### B – Force-Load kostet Geld

1. Wieder Geld geben: `/lcbank give players @s 5000`
2. Auf der Claim-Map Shift+Klick (oder Force-Load-Modus) für einen geclaimten Chunk.
3. **Erwartung:** **50** Kupfer weniger (`forceLoadPrice`).

### C – Schutz-Einstellungen (FTB Chunks Properties)

1. Team-Menü / FTB Chunks → **Properties** (wie in `REQUIREMENTS.md`).
2. z.B. **Allow Explosion Damage** auf `False` (Schutz an).
3. Mit genug Guthaben: Änderung bleibt.
4. Konto leeren, erneut Schutz aktivieren:
5. **Erwartung:** Einstellung springt zurück; Chat: *"...Your change was reverted."*

### D – Upkeep

1. Mindestens **1 Chunk** geclaimt, Schutz aktiv (z.B. Explosion = False).
2. `/lcbank give players @s 5000` (genug für `b × n`, hier z.B. 10 × 1 = 10 pro Periode).
3. **2 Minuten warten** (`upkeepPeriodMinutes = 2`).
4. **Erwartung:** Geld wird abgezogen (10 × Anzahl Claims).
5. Konto leer lassen, weitere Periode abwarten:
6. **Erwartung:** Schutz auf Minimum (Mob Grief/Explosion/PvP = True, Block-Modi = Public); Chat: *"...Protections were reset..."*

### E – Party-Team (optional)

1. Party/Team über FTB Teams erstellen.
2. Team-Bankkonto am ATM wählen ( erscheint nach Team-Erstellung ).
3. `/lcbank give players @s 5000` → Geld auf **Team-Konto** einzahlen (ATM, Konto wechseln).
4. Als **Member** (nicht Owner/Officer): Claim versuchen.
5. **Erwartung:** Abgelehnt – nur Owner/Officer dürfen kaufen.

---

## 5. Auf externem Server testen

JAR kopieren:

```text
build\libs\lc_ftb_hook-1.0.0.jar
```

In `mods/` eines NeoForge-**1.21.1**-Servers zusammen mit:

| Mod | Hinweis |
|-----|---------|
| Lightman's Currency | gleiche MC-Version |
| FTB Library | Pflicht für FTB |
| FTB Teams | Pflicht |
| FTB Chunks | Pflicht |
| Architectury | meist über FTB mitgeliefert |

Server-Config: `world/serverconfig/lc_ftb_hook-server.toml` (gleiche Werte wie oben).

---

## Häufige Probleme

| Problem | Lösung |
|---------|--------|
| Gradle-Fehler `major version 70` | JDK **21** setzen (siehe oben) |
| Claim kostet nichts | `claimPrice = 0` in Config? Datei neu laden |
| FTB-Map geht nicht auf | `runClient` nutzen, nicht nur Server |
| Kein Geld-Befehl | Cheats an, `/lcbank` braucht LC + OP |
| Config wirkt nicht | Welt verlassen / Client neu starten |

---

## Nützliche Befehle

```text
/lcbank give players @s <betrag>
/ftbchunks info
/ftbteams info
```
