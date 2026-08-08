package me.psikuvit.cashClash.util.enums;

public enum TeamColor {
    /**
     * Team color identifiers
     */
    RED("Red","<red>"),

    BLUE("Blue","<blue>");

    private final String displayName;
    private final String miniMessageColor;

    TeamColor(String displayName, String miniMessageColor) {
        this.displayName = displayName;
        this.miniMessageColor = miniMessageColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMiniMessageColor() {
        return miniMessageColor;
    }

    public String getColoredName() {
        return miniMessageColor + displayName + "</" + displayName.toLowerCase() + ">";
    }

    /**
     * The legacy numeric team identifier (1=RED, 2=BLUE) still used at the edges of the codebase
     * (persistence, public method signatures) - prefer TeamColor itself as a map key/identity
     * over this int wherever possible.
     */
    public int getTeamNumber() {
        return this == RED ? 1 : 2;
    }

    public static TeamColor fromTeamNumber(int teamNumber) {
        return teamNumber == 1 ? RED : BLUE;
    }

    public TeamColor opposite() {
        return this == RED ? BLUE : RED;
    }

}
