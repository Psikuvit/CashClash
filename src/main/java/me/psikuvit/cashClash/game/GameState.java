package me.psikuvit.cashClash.game;

/**
 * Represents the current state of a game session
 */
public enum GameState {
    WAITING,
    SHOPPING,
    COMBAT,
    ENDING;

    public int getRound() {
        if (this == WAITING || this == ENDING) return 0;
        return Character.getNumericValue(this.name().charAt(6));
    }
}

