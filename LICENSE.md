# License

**LC FTB Hook** — Copyright © 2026 Malik Aljazzar. All rights reserved.

LC FTB Hook is an **addon** that bridges [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) with [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-neoforge), [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-neoforge), and [FTB Library](https://www.curseforge.com/minecraft/mc-mods/ftb-library-neoforge) on NeoForge, turning land claiming and protection into a paid economy with Regions and a chunk marketplace. This license covers only LC FTB Hook's own code, assets, and documentation — the addon's Java source, mixins, config, lang files, and everything under `docs/`, `wiki/`, and the root-level `.md` files. It does not cover, grant, or restrict any rights to Lightman's Currency, FTB Chunks/Teams/Library, Xaero's World Map, or any other mod it integrates with; those remain separate works under their own licenses (see §4). LC FTB Hook is not developed, endorsed, or officially supported by the authors of any of those mods.

This is a proprietary, source-available license, not an open-source one (not MIT, GPL, Apache, or any OSI-approved license).

## 1. What this repository is for

The source is published on GitHub for **transparency, issue tracking, and community visibility only** — so players can see what the addon does, report bugs against real code, and verify its behavior. Being able to read the source does not grant any license to use, copy, modify, compile, or redistribute it. Everything below is the actual grant of rights; the code being visible is incidental to it.

Want a feature added? Open a [pull request](https://github.com/ManOfKott/lc-ftb-hook/pulls) — see §5 for the terms contributions are accepted under. A feature request opened as an issue with no pull request attached may simply never get built.

## 2. What you may do

Without any additional permission, you may:

- **Download and run the compiled addon** (the `.jar` released on CurseForge and GitHub Releases) alongside Lightman's Currency and FTB Chunks/Teams/Library, on any world or server, for personal or public use, unmodified.
- **Include the unmodified, officially-distributed `.jar`** — of LC FTB Hook and its required dependencies, per each of their own licenses — in a modpack, public or private, monetized or not, provided the modpack links back to LC FTB Hook's official CurseForge or GitHub Releases page for the download rather than rehosting the file itself, and doesn't claim authorship of it.
- **Read the source** in this repository to understand addon behavior, debug its interaction with Lightman's Currency, FTB Chunks/Teams/Library, Xaero's World Map, or your own server setup, or verify a claim made in an issue or bug report.
- **Open issues and submit pull requests** against this repository (see §5).
- **Quote or reference** small excerpts of the source (a method, a config snippet) in your own documentation, guides, or bug reports, with attribution to this repository.

## 3. What requires the copyright holder's written permission

Everything not listed in §2 is reserved, including but not limited to:

- Compiling the source yourself and distributing the resulting jar, modified or not.
- Modifying the source and distributing a fork, patch, or derivative addon — including one that only changes numbers in the config defaults, translations, or textures.
- Rehosting the compiled jar anywhere other than the official CurseForge listing or this repository's GitHub Releases (mirrors, other mod sites, direct file shares).
- Decompiling, reverse-engineering, or extracting the source from the compiled jar for any purpose beyond personal debugging.
- Using this addon's name, the "Malik Aljazzar" name, or any of the project's branding/logo assets to imply endorsement of, or affiliation with, Lightman's Currency, any of the mods in §4, or another project.
- Any commercial use of the source code itself (as opposed to normal, non-commercial-in-itself gameplay use of the compiled addon, which is covered by §2).

If you want to do one of these things, ask first — contact information is in §7.

## 4. Third-party dependencies

LC FTB Hook is built on top of, and **requires**, [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency), [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-neoforge), [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-neoforge), and [FTB Library](https://www.curseforge.com/minecraft/mc-mods/ftb-library-neoforge) to run — it has no standalone functionality without them. It also optionally integrates with [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map) and NeoForge itself. None of those projects are covered by this license — they're separate works under their own respective licenses, obtained separately from their own official listings, and nothing here grants or restricts any rights to them. Any permission granted in §2 or §3 for LC FTB Hook does not extend to Lightman's Currency, FTB Chunks/Teams/Library, Xaero's World Map, or any other mod in this list.

## 5. Contributions

By submitting a pull request, patch, or other contribution to this repository, you agree that:

- You have the right to submit the contribution (it's your own original work, or you have the right to relicense it).
- You grant the copyright holder a perpetual, worldwide, royalty-free license to use, modify, and relicense your contribution as part of this project, under this license or any future license the project adopts.
- You retain your own copyright in your contribution; you're not required to (and don't) transfer ownership of it.

Contributions are still expected to follow the same restrictions as the rest of the codebase once merged — a contribution doesn't carve out its own open-source island inside an otherwise all-rights-reserved project. Feature requests are welcome as pull requests; a plain request with no code attached is not a commitment that it gets built.

## 6. No warranty

This addon is provided "as is," without warranty of any kind, express or implied, including but not limited to warranties of merchantability, fitness for a particular purpose, and non-infringement. The copyright holder is not liable for any damages — including lost data, corrupted worlds, or server downtime — arising from the use of this addon or its interaction with Lightman's Currency, FTB Chunks/Teams/Library, Xaero's World Map, or any other mod listed in §4, to the maximum extent permitted by law.

## 7. Requesting permission / contact

For anything in §3 — forks, redistribution, modified copies, or any other use beyond §2 — open a [GitHub issue](https://github.com/ManOfKott/lc-ftb-hook/issues) describing what you'd like to do. Permission granted for one case doesn't set a precedent for others; each request is considered on its own.

## 8. Changes to this license

The copyright holder may update these terms for future versions of the addon. Changes apply going forward and don't retroactively revoke permissions already granted in writing for a specific past use.
