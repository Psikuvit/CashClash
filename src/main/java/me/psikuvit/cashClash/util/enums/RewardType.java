package me.psikuvit.cashClash.util.enums;

/**
 * Types of achievement-style coin rewards granted through
 * {@link me.psikuvit.cashClash.manager.player.RewardManager}. Each type carries the
 * {@code messages.yml} key to announce it with (or {@code null} if the call site sends its
 * own message) and whether it's eligible to also trigger the Investor's Set bonus.
 */
public enum RewardType {
    KILL("economy.kill-transfer-received", true),
    KILL_ASSIST("economy.kill-transfer-assist", false),
    OBJECTIVE_CTF_CAPTURE(null, true),
    OBJECTIVE_KC_CONFIRM(null, true),
    BONUS(null, false),
    CTF_HOLD_BONUS("gamemode-ctf.flag-captured-bonus", false),
    KC_CONFIRM_BONUS("gamemode-kc.money-tag-bonus-coins", false),
    PTP_KILL_STREAK_BONUS("gamemode-ptp.kill-bonus", false),
    // "economy.round-money-earned" is sent by EconomyManager itself, since it needs the pool
    // as well as the amount actually granted.
    ROUND_DISTRIBUTION(null, false),
    SUPPLY_DROP("cashquake.supply-drop-reward", false),
    CASH_BLASTER_VORTEX(null, false);

    private final String messageKey;
    private final boolean investorEligible;

    RewardType(String messageKey, boolean investorEligible) {
        this.messageKey = messageKey;
        this.investorEligible = investorEligible;
    }

    /**
     * The {@code messages.yml} key to announce this reward with, or {@code null} if the
     * call site sends its own message instead.
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * Whether this reward type should also trigger the Investor's Set bonus check.
     */
    public boolean isInvestorEligible() {
        return investorEligible;
    }
}
