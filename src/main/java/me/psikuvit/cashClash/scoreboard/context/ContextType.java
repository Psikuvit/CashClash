package me.psikuvit.cashClash.scoreboard.context;

/**
 * Enum representing different scoreboard contexts
 * Used to determine which scoreboard to display based on player state
 */
public enum ContextType {
    LOBBY("LOBBY"),
    GAME_DEFAULT("DEFAULT_GAME"),
    CTF("CTF"),
    PTP("PTP");

    private final String displayName;

    ContextType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this context is a game context (not lobby)
     */
    public boolean isGameContext() {
        return this != LOBBY;
    }

    /**
     * Check if this context is a lobby context
     */
    public boolean isLobbyContext() {
        return this == LOBBY;
    }
}

