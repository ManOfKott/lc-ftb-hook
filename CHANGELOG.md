# Changelog

All notable changes to LC FTB Hook are documented in this file.

## [3.0.0]

### Regions (replaces the old Land/Build chunk split)
- Teams can now create any number of named **Regions** to subdivide their claimed territory, each with its own independent protection settings (mob griefing, explosions, PvP, block interact, block edit, entity interact) instead of one flat team-wide setting.
- New **Region List** screen (opened from a new button on the FTB Teams team screen) supports creating, renaming, deleting, and drag-and-drop reordering regions.
- New **Region Settings** screen shows live chunk count, per-property prices, and pending (queued) changes before they take effect.
- Every team keeps an auto-created, non-deletable "Default" region; existing claims migrate automatically.
- Upkeep dismantle order (which protections get dropped first when upkeep can't be paid) is now computed per-region, in the region list's own order.
- FTB Chunks' and Xaero's World Map now draw a border between two regions belonging to the same team, not just between different teams.

### Chunk Marketplace
- Officers/owners can list state-owned chunks for sale ("Sell as Country"), and players can buy them individually or as a team.
- Players can privately buy a chunk from their own team's territory; the chunk stays part of the team's land (upkeep/dismantle unaffected) but the private owner can loosen (never tighten) that one chunk's protection below the region's baseline.
- Private owners can resell their chunk, set a custom sale price, or add a short custom label shown in the chunk's info panel (e.g. "Region: Village - Shop").
- Officers/owners can expropriate a privately-owned chunk back to the team, with configurable compensation.
- A new **whitelist/blacklist** access list lets a private owner grant or deny specific players access to their chunk, independent of the team's own privacy settings.
- New **Team** privacy tier: alongside Public/Allies/Private, chunk privacy modes can now also be set to "whole team, any rank" — distinct from "Private" (owner/officers only, or the individual chunk owner if privately owned).
- Officers and the team owner always bypass a privately-owned chunk's protection within their own land, even if they're on that chunk's blacklist.
- Whitelist/blacklist settings now also apply when a chunk's own setting is looser than what the region would otherwise require, closing a gap where they could be bypassed entirely.
- A freshly **claimed** chunk can't be listed for sale until the team's next successful upkeep settlement (prevents instantly flipping newly-claimed land). This no longer applies to a chunk that was just privately bought — that one can be resold immediately.
- Real Emerald/Emerald Block currency (Lightman's Currency's separate "Emeralds" chain, distinct from its "Coins" chain's Emerald Coin) can no longer be used to price a chunk sale, matching the existing restriction on Emerald Coins.
- Offline (or never-seen-this-session) chunk owners now show their real name and skin in info panels instead of a raw UUID fragment.
- The always-visible balance panel on the World Map no longer includes a stray Emerald-chain balance alongside the actual (Coins-chain) balance.

### Xaero's Minimap / World Map integration
- Updated for compatibility with Xaero's Minimap 26.5.0 and World Map 1.46.0.
- Added fault tolerance across every Xaero integration point: a broken or incompatible Xaero API call now logs once and disables gracefully instead of crashing the game, and players without Xaero's World Map installed at all no longer risk a hard crash from the integration code.
- New always-visible money panel on the World Map (claim price, personal/private/country balance, force-load upkeep) plus a hover panel per claimed chunk (owner with skin head, region + custom label, sale price, and every protection property).
- New right-click marketplace/region menu on the World Map (Sell/Sell as Country/Buy/Buy as Country/Assign to Region), alongside the existing claim/unclaim/forceload options.

### Fixes
- Fixed a pricing bug where re-editing a region property to a different but same-tier value (e.g. Private → Allies after already queuing Private → Public) incorrectly showed as "pending" instead of applying immediately, because the comparison used the previously-queued value instead of the region's true live value.
- Fixed a crash opening Region Settings from Xaero's World Map (null parent reference on Back/Delete).
- Fixed the dev environment silently keeping stale-version Xaero jars around after a version bump.
- Fixed a missing translation causing raw text (`message.lc_ftb_hook.marketplace_expropriated`) to display after an expropriation.

### Other
- "Next payment due" is now shown in chat, the Region Settings screen, and the Region List screen's tooltips.
