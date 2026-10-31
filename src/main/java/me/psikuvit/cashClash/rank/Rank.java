package me.psikuvit.cashClash.rank;

/**
 * A staff rank shown next to a player's name, set with {@code /cc rank} and saved with the
 * player's data.
 */
public enum Rank {
    OWNER("Owner", "ranks.owner-tag"),
    ADMIN("Admin", "ranks.admin-tag");

    private final String displayName;
    private final String tagMessageKey;

    Rank(String displayName, String tagMessageKey) {
        this.displayName = displayName;
        this.tagMessageKey = tagMessageKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * The messages.yml key of the tag shown before the player's name.
     */
    public String getTagMessageKey() {
        return tagMessageKey;
    }
}
