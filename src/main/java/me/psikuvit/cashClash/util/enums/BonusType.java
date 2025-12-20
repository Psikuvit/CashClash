package me.psikuvit.cashClash.util.enums;

/**
 * Types of bonuses players can earn
 */
public enum BonusType {
    // Endgame bonuses
    MOST_KILLS(10000),
    MOST_DAMAGE(10000),
    UNDERDOG(10000),

    // Midgame bonuses
    SURVIVOR(3000),
    CLOSE_CALLS(5000),
    RAMPAGE(5000),
    FIRST_BLOOD(2000),
    COMEBACK_KID(2000);

    private final long reward;

    BonusType(long reward) {
        this.reward = reward;
    }

    public long getReward() {
        return reward;
    }
}

