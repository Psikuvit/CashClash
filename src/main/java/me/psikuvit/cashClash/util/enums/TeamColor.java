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

}
