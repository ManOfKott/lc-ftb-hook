package dev.malik.lcftbhook.teams;

/**
 * Shared constants for who may buy a marketplace listing - see
 * {@link dev.malik.lcftbhook.data.Region#buyerAccess()}. Originally a real
 * team-wide FTB {@code TeamProperty} (one setting for the whole team); moved
 * to a per-region mod-native field (same architecture shift {@code
 * allowPrivateSelling} already went through) so different parts of a team's
 * territory can open up to different buyers instead of one blanket setting.
 */
public final class MarketplaceTeamProperties {
    public static final String ACCESS_ALL = "ALL";
    public static final String ACCESS_TEAM = "TEAM";
    public static final String ACCESS_ALLIES = "ALLIES";

    private MarketplaceTeamProperties() {
    }
}
