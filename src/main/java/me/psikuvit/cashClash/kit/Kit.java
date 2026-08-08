package me.psikuvit.cashClash.kit;

/**
 * Starter kits for Round 1
 */
public enum Kit {
    ARCHER("Archer"),
    HEALER("Healer"),
    TANK("Tank"),
    SCOUT("Scout"),
    LUMBERJACK("Lumberjack"),
    PYROMANIAC("Pyromaniac"),
    GHOST("Ghost"),
    FIGHTER("Fighter"),
    SPIDER("Spider"),
    BOMBER("Bomber");

    private final String displayName;

    Kit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
