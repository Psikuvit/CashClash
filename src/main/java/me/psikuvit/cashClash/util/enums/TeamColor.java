package me.psikuvit.cashClash.util.enums;

import org.bukkit.Color;

public enum TeamColor {
    /**
     * Team color identifiers
     */
    RED("Red","<red>", Color.fromRGB(220, 20, 20)),

    BLUE("Blue","<blue>", Color.fromRGB(40, 90, 255));

    private final String displayName;
    private final String miniMessageColor;
    private final Color particleColor;

    TeamColor(String displayName, String miniMessageColor, Color particleColor) {
        this.displayName = displayName;
        this.miniMessageColor = miniMessageColor;
        this.particleColor = particleColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMiniMessageColor() {
        return miniMessageColor;
    }

    /**
     * The dust colour a team's placed items and abilities are drawn in.
     */
    public Color getParticleColor() {
        return particleColor;
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
