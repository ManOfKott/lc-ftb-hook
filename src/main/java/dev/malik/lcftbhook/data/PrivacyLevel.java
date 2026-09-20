package dev.malik.lcftbhook.data;

/**
 * Mod-owned 4-tier access model for the 3 privacy-mode protection properties
 * (block interact/edit, entity interact), replacing FTB Teams' own 3-value
 * {@code PrivacyMode} as the stored value type on Regions/chunk overrides -
 * FTB's enum has no room for a 4th tier. ALLIES/TEAM/PRIVATE all cost the
 * same single flat config price (see {@code ProtectionResolution#strictness})
 * and are freely interchangeable without it counting as tightening/loosening
 * - they only differ in WHO exactly gets access within "protected":
 * <ul>
 *     <li>{@code PUBLIC} - everyone (free, the minimum/default value)</li>
 *     <li>{@code ALLIES} - this team + allied teams (delegates to FTB's own ally check)</li>
 *     <li>{@code TEAM} - this team only, any rank (what FTB itself calls "Private")</li>
 *     <li>{@code PRIVATE} - the chunk's individual marketplace owner (+ their
 *     own custom whitelist/blacklist) if the chunk has one, otherwise this
 *     team's officers/owner only (see {@code ChunkTeamDataRegionPrivacyMixin})</li>
 * </ul>
 */
public enum PrivacyLevel {
    PUBLIC, ALLIES, TEAM, PRIVATE
}
