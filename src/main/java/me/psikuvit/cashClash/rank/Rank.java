package me.psikuvit.cashClash.rank;

/**
 * A staff rank, set with {@code /cc rank} and saved with the player's data.
 */
public enum Rank {
    OWNER("Owner"),
    ADMIN("Admin");

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
