package me.psikuvit.cashClash.manager.game;
 
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;

import java.util.UUID;
 
/**
 * Manages economy and transactions
 */
public class EconomyManager {

    public static void distributeRoundMoney(GameSession session) {
        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) return;

        int totalKills = roundData.getTotalRoundKills();
        long killPool = totalKills * 1000L;
        
        // Total of this pool is given to all players (requirement says "all 8 players", we use session.getPlayers().size() but the logic should handle 8)
        // Actually, the requirement says "The total of this pool is given to all 8 players".
        // This could mean each player gets the total pool, OR the pool is divided.
        // "Every kill adds 1000$ to a money pool. At the end of the round, the total of this pool is given to all 8 players."
        // Usually, this means each player gets the total pool amount if it's over 15k.
        // "Players are given a minimum of 15k per round. This 15k does not “add” to the pool. It is just the minimum players get if the pool is under 15k"
        
        long finalAmount = Math.max(killPool, 15000);

        Messages.broadcast(session.getPlayers(), "economy.round-money-distributed",
                "pool", String.format("%,d", killPool),
                "amount", String.format("%,d", finalAmount));
        
        Messages.debug("ECONOMY: Round " + session.getCurrentRound() + " - Total Kills: " + totalKills + " Pool: " + killPool + " Final per player: " + finalAmount);

        for (UUID uuid : session.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                ccp.addCoins(finalAmount);
            }
        }
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
            case 6, 7 -> cfg.getLateRoundStealPercentage();
            default -> 0.0;
        };
    }

    public static boolean transferMoney(CashClashPlayer sender, CashClashPlayer receiver, long amount, GameSession session) {
        if (!sender.canAfford(amount)) {
            return false;
        }
 
        double fee = getTransferFee(session);
        long netAmount = (long) (amount * (1 - fee));
 
        Messages.debug("ECONOMY", "Transfer: " + sender.getPlayer().getName() + " -> " + receiver.getPlayer().getName() + " Amount: " + amount + " Fee: " + fee + " Net: " + netAmount);

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
}
