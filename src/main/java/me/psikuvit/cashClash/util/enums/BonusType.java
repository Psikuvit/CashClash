package me.psikuvit.cashClash.util.enums;

import me.psikuvit.cashClash.CashClashPlugin;

/**
 * The four bonuses players can earn, each a flat amount (config: {@code rounds.player-bonus-amount})
 * to that player only: First Blood (first kill of the round), Killstreak (every Nth kill in an
 * uninterrupted streak), and Most Kills / Most Damage (session-wide, awarded once at game end).
 */
public enum BonusType {
    FIRST_BLOOD,
    KILLSTREAK,
    MOST_KILLS,
    MOST_DAMAGE;

    public long getReward() {
        return CashClashPlugin.getInstance().getConfigManager().getPlayerBonusAmount();
    }
}
