package me.psikuvit.cashClash.util.enums;

/**
 * The four bonuses players can earn, each a flat 2000 coins to that player only:
 * First Blood (first kill of the round), Killstreak (every 4th kill in an uninterrupted
 * streak), and Most Kills / Most Damage (session-wide, awarded once at game end).
 */
public enum BonusType {
    FIRST_BLOOD(2000),
    KILLSTREAK(2000),
    MOST_KILLS(2000),
    MOST_DAMAGE(2000);

    private final long reward;

    BonusType(long reward) {
        this.reward = reward;
    }

    public long getReward() {
        return reward;
    }
}
