package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;

/**
 * Manages economy and transactions
 */
public class EconomyManager {

    public static long getKillReward(GameSession session, CashClashPlayer killer) {
        int round = session.getCurrentRound();
        ConfigManager cfg = ConfigManager.getInstance();

        if (round == 1) return cfg.getRound1KillReward();
        if (round == 2 || round == 3) {
            int kills = session.getCurrentRoundData().getKills(killer.getUuid());
            if (kills == 1) return cfg.getRound2Bonus() / 3; // approx (configurable if needed)
            if (kills == 2) return cfg.getRound2Bonus() / 2;
            return cfg.getRound2Bonus() / 2; // fallback
        }
        return 0;
    }

    public static long calculateStealAmount(GameSession session, CashClashPlayer victim) {
        int round = session.getCurrentRound();
        ConfigManager cfg = ConfigManager.getInstance();
        if (round == 4 || round == 5) {
            double pct = cfg.getLateRoundStealPercentage();
            return (long) (victim.getCoins() * pct);
        }
        return 0;
    }

    public static double getTransferFee(GameSession session) {
        int round = session.getCurrentRound();
        ConfigManager cfg = ConfigManager.getInstance();
        return switch (round) {
            case 1 -> cfg.getRound1TransferFee();
            case 2, 3 -> cfg.getRound23TransferFee();
            case 4, 5 -> cfg.getRound45TransferFee();
            default -> 0.0;
        };
    }

    public static boolean transferMoney(CashClashPlayer sender, CashClashPlayer receiver, long amount, GameSession session) {
        if (!sender.canAfford(amount)) {
            return false;
        }

        double fee = getTransferFee(session);
        long netAmount = (long) (amount * (1 - fee));

        sender.deductCoins(amount);
        receiver.addCoins(netAmount);

        return true;
    }

    public static void applyForfeitPenalty(GameSession session, CashClashPlayer player) {
        // Players go negative on investments when forfeiting
        // Set deaths to max so investment will result in a loss
        if (player.getCurrentInvestment() != null) {
            player.getCurrentInvestment().setDeathsToMax();
        }
    }

    public static void giveRoundStartMoney(GameSession session, CashClashPlayer player) {
        int round = session.getCurrentRound();
        ConfigManager cfg = ConfigManager.getInstance();
        switch (round) {
            case 1 -> player.addCoins(cfg.getRound1Start());
            case 2 -> player.addCoins(cfg.getRound2Bonus());
            case 3 -> player.addCoins(cfg.getRound3Bonus());
            case 4 -> player.addCoins(cfg.getRound4Bonus());
            case 5 -> {
                if (player.getCoins() < cfg.getRound5Minimum()) {
                    player.addCoins(cfg.getRound5Bonus());
                }
            }
        }
    }
}
