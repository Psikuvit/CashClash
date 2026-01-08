package me.psikuvit.cashClash.chat;

/**
 * Represents the available chat channels.
 */
public enum ChatChannel {
    /**
     * Global chat - visible to all players.
     */
    GLOBAL("Global", "<white>", ""),

    /**
     * Party chat - visible only to party members.
     */
    PARTY("Party", "<aqua>", "<dark_aqua>[Party] </dark_aqua>"),

    /**
     * Team chat - visible only to team members during a game.
     */
    TEAM("Team", "<green>", "<dark_green>[Team] </dark_green>"),

    /**
     * Game chat - visible to all players in the same game.
     */
    GAME("Game", "<yellow>", "<gold>[Game] </gold>");

    private final String displayName;
    private final String nameColor;
    private final String prefix;

    ChatChannel(String displayName, String nameColor, String prefix) {
        this.displayName = displayName;
        this.nameColor = nameColor;
        this.prefix = prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNameColor() {
        return nameColor;
    }

    public String getPrefix() {
        return prefix;
    }
}

