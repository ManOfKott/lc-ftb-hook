# Changelog

All notable changes to LC FTB Hook are documented in this file.

## [3.1.0]

### New Features
- Protection settings now show a description, price, and (for Public/Allies/Team/Private settings) what each tier means when you hover over them.
- "Next payment" for upkeep and force-loads now shows a live, ticking minutes:seconds countdown instead of a static whole-minutes value.
- Force-loaded chunks now show a red hatch on Xaero's World Map, alongside the existing marketplace hatch.
- Claim visibility (FTB Teams' own Public/Allies/Team map setting) can now be unlocked via config instead of always being forced to Public.
- Added a placeholder 7th protection setting, "Sable Sublevels" (grayed out, coming soon).
- The Region Settings upkeep summary now also breaks down force-load costs, the same way the region list already does.
- Every payment this mod makes (upkeep, claim purchase/refund, marketplace buy/sell, expropriation, party-disband fund transfer) now shows up in the relevant account's Lightman's Currency bank log instead of silently moving money with no record.
- Added a pooled server bank account that collects upkeep and claim payments instead of destroying that money outright - accessible to any op via `/lc_ftb_hook server_account`.
- Protection prices can now be set in a smaller unit (10 = 1 copper, rounded down), so a price can be a fraction of a copper per chunk.
- Added `/lc_ftb_hook config list|get|set`, letting admins view and change most server config values live, without editing the config file or restarting.
- The war system's on/off switch can now be flipped at runtime: turning it off immediately discards every active and pending war and hides the war button for everyone online, no restart needed.
- Region List and Region Settings screens now use consistent icons, spacing, and a priority order that reads top-to-bottom the way people expect.
- The Region List's upkeep tooltip now shows the same per-region, per-property price breakdown as the full `/upkeep_details` command instead of one flat total.
- Default upkeep period changed to 5 minutes.
- The map/claim-screen info panel now shows whether a chunk is currently force-loaded, or has a force load/unload queued that hasn't taken effect yet.
- The private-chunk-override screen now shows your saved preference struck-through in gray next to the actually-enforced value whenever they differ (e.g. the region can't currently afford to deliver it), instead of hiding one or the other - and it's never locked from editing anymore, so you can always change what you want even while it's not currently in effect.
- If a team officer deliberately loosens a region's protection across a real tier (not just switching between Allies/Team/Private), any private chunk overrides in that region that are now stricter than the new baseline are cleared - a region-wide decision now genuinely applies to privately-owned chunks in it too.
- A force-loaded chunk now shows red (not blue) in the map/claim-screen info panel to match the Xaero map hatch, and pending/dropped-for-non-payment force-loads now also show a red hatch on Xaero's World Map, not just currently-active ones.
- A force-load that gets dropped because upkeep can no longer afford it is no longer lost outright - it's queued the same as a brand-new request and silently resumes the moment the team can afford it again.
- Force-loads are now dismantled one chunk at a time (same as protections/wars) and slot into the same priority order, between wars and protections, instead of being dropped all at once as a separate last-resort step. `/upkeep_priority` now lists force-loaded chunks alongside protections and wars in that same combined order.

### Fixes
- Fixed team-account spending permissions (buying/reselling marketplace chunks, force-loading chunks, placing tax collectors, declaring/ending wars, and the `upkeep_details`/`upkeep_priority` commands) being hardcoded to "Officer or better" regardless of the team's own settings. It now follows whatever access level the team owner has actually configured on the linked Lightman's Currency bank account (Owner only / Owner + Admins / everyone) - set a chunk-selling team to "Member" access and members can spend from it, exactly as configured.
- Fixed the Xaero marketplace-chunk hatch still showing team-tinted colors instead of a flat marketplace color, caused by draw order rather than the colors themselves.
- Fixed private chunk overrides being unreachable/uneditable for a chunk owned in another team's territory - affected the chunk override screen and both "Sell..." buttons.
- Fixed a privately-owned chunk's Allies/Team protection tier locking out its own private owner.
- Fixed the private-chunk-override screen showing raw `true`/`false` instead of "Protected"/"Unprotected", with color coding that didn't match the actual protection state.
- Fixed the World Map's private/country balance panels always showing empty regardless of actual balance.
- Fixed a misleading "not yours to sell" message that covered three different, unrelated reasons a chunk couldn't be listed.
- Fixed the map/claim-screen protection info panel showing an inverted color scheme and per-viewer information instead of the chunk's real, shared setting.
- Fixed dismantle/restoration/suspension chat messages not naming which Region (or which war) a line referred to.
- Fixed an outgoing war stuck "queued, not enough balance" never being reported anywhere.
- Fixed expropriating a privately-owned chunk never telling the affected owner - they now get a chat message (if online) naming who expropriated their chunk(s) and how much compensation they received.
- The server bank account can no longer silently overflow into a negative balance from an extreme accumulated total - once it would, further sink payments are burned instead of deposited, same as before the account existed.
- Fixed the server bank account showing up as "Unknown's Bank Account" instead of a proper name.
- Fixed the private chunk override screen's protection tooltip showing a price - loosening your own chunk below the region's baseline has never actually cost anything extra.
- Fixed `/upkeep_priority`'s output being a single uncolored wall of text.
- Fixed "Next payment"/"Period" showing as two separate, often-identical-looking lines in chat and tooltips - merged into one everywhere.
- Fixed the new war-system runtime toggle missing a currently-inactive team's stale war reference.
- Fixed a privately-owned chunk staying protected (both in display and in actual enforcement) after its region's protection was dismantled for non-payment - the chunk's own saved preference is unaffected and resumes automatically once the region's protection is restored.

### Dependencies
- Now built against NeoForge 21.1.251 and FTB Library 2101.1.36. The mod's own minimum required versions are unchanged (NeoForge 21.1.250, FTB Library 2101.1.35), so existing servers aren't forced to update immediately.

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
