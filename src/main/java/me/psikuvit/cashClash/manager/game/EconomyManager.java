package me.psikuvit.cashClash.manager.game;
 
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.RewardType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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
        
        long finalAmount = Math.max(killPool, 15000);

        Messages.broadcast(session.getPlayers(), "economy.round-money-distributed",
                "pool", String.format("%,d", killPool),
                "amount", String.format("%,d", finalAmount));
        
        Messages.debug("ECONOMY: Round " + session.getCurrentRound() + " - Total Kills: " + totalKills + " Pool: " + killPool + " Final per player: " + finalAmount);

        for (UUID uuid : session.getPlayers()) {
            session.getRewardManager().grant(uuid, RewardType.ROUND_DISTRIBUTION, finalAmount);
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

    public static void giveRoundStartMoney(GameSession session, CashClashPlayer player) {
        // Obsolete: Money is now distributed at the end of the round via distributeRoundMoney
    }

    /**
     * Resolves all player investments at end of round.
     * Awards bonus, breaks even, or applies penalty based on deaths this round.
     * Called at end of each combat phase, not at game end.
     */
    public static void resolveRoundInvestments(GameSession session) {
        for (CashClashPlayer ccp : session.getCashClashPlayers()) {
            Investment investment = ccp.getCurrentInvestment();
            if (investment == null) continue;

            Player p = Bukkit.getPlayer(ccp.getUuid());
            long returnAmount = investment.calculateReturn();
            String typeName = investment.getType().name().replace("_", " ");

            if (investment.isProfitable()) {
                // 0-1 deaths: Bonus
                ccp.addCoins(returnAmount);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "round.investment-success");
                    Messages.send(p, "round.investment-success-detail",
                            "type_name", typeName,
                            "amount", String.format("%,d", returnAmount));
                    Messages.send(p, "round.investment-success-deaths",
                            "deaths", String.valueOf(investment.getDeaths()));
                    SoundUtils.play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else if (investment.isBreakEven()) {
                // 2 deaths: Break even
                ccp.addCoins(returnAmount);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "round.investment-breakeven");
                    Messages.send(p, "round.investment-breakeven-detail",
                            "type_name", typeName,
                            "amount", String.format("%,d", returnAmount));
                    Messages.send(p, "round.investment-breakeven-deaths",
                            "deaths", String.valueOf(investment.getDeaths()));
                    SoundUtils.play(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            } else {
                // 3+ deaths: Loss
                long penalty = investment.getType().getNegativeReturn();
                ccp.deductCoins(penalty);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "round.investment-failed");
                    Messages.send(p, "round.investment-failed-detail",
                            "type_name", typeName,
                            "amount", String.format("%,d", penalty));
                    Messages.send(p, "round.investment-failed-deaths",
                            "deaths", String.valueOf(investment.getDeaths()));
                    SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                }
            }

            // Clear the investment after resolution
            ccp.setCurrentInvestment(null);
            ccp.setInvestedCoins(0);
        }
    }
}
