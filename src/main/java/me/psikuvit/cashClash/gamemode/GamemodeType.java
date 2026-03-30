package me.psikuvit.cashClash.gamemode;

/**
 * Enum for available gamemodes in Cash Clash
 */
public enum GamemodeType {
    PROTECT_THE_PRESIDENT("Protect the President"),
    CAPTURE_THE_FLAG("Capture the Flag");

    private final String displayName;

    GamemodeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

