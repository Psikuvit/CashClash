package me.psikuvit.cashClash.game;

/**
 * Represents the current state of a game session
 */
public enum GameState {
    WAITING,
    ROUND_1_SHOPPING,
    ROUND_1_COMBAT,
    ROUND_2_SHOPPING,
    ROUND_2_COMBAT,
    ROUND_3_SHOPPING,
    ROUND_3_COMBAT,
    ROUND_4_SHOPPING,
    ROUND_4_COMBAT,
    ROUND_5_SHOPPING,
    ROUND_5_COMBAT,
    ENDING;

    public boolean isCombat() {
        return this.name().contains("COMBAT");
    }

    public int getRound() {
        if (this == WAITING || this == ENDING) return 0;
        return Character.getNumericValue(this.name().charAt(6));
    }
}

