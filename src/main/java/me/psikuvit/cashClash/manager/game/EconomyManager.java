package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.CashClashPlugin;
 
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.RewardType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages economy and transactions
 */
public class EconomyManager {

    /**
     * The round the economy is on: one more than the rounds decided so far. A draw doesn't count,
     * so the round after one pays and transfers as though the drawn round never happened.
     */
    public static int getEconomyRound(GameSession session) {
        return session.getRoundWins(1) + session.getRoundWins(2) + 1;
    }

    /**
     * The pool the economy round's buy phase pays out, or 0 if there's none. Past the last
     * configured round it's the last pool again.
     */
    public static long getRoundPool(GameSession session) {
        return CashClashPlugin.getInstance().getConfigManager().getRoundPool(getEconomyRound(session));
    }

    /**
     * The pool a kill takes its money from: the next buy phase's. In the last configured round,
     * and any extra rounds after it, that's the last pool again.
     */
    private static long getNextRoundPool(GameSession session) {
        return CashClashPlugin.getInstance().getConfigManager().getRoundPool(getEconomyRound(session) + 1);
    }

    private static long standardShare(long pool) {
        return pool / Math.max(1, CashClashPlugin.getInstance().getConfigManager().getRoundPoolSplit());
    }

    /**
     * Pays every player their part of the economy round's pool at the start of its buy phase, on
     * top of whatever they have left over: an equal share, plus the kill and assist transfers
     * they won in the round just played, minus the ones they lost (never below nothing). Only
     * the first buy phase announces the share; later ones announce the total
     * ({@link #sendRoundEarnings}).
     */
    public static void payRoundShare(GameSession session) {
        long pool = getRoundPool(session);
        if (pool <= 0) return;

        long share = standardShare(pool);
        RoundData previousRound = session.getPreviousRoundData();
        for (UUID uuid : session.getPlayers()) {
            long transfers = previousRound != null ? previousRound.getPoolTransfers(uuid) : 0;
            long paid = session.getRewardManager().grant(uuid, RewardType.ROUND_DISTRIBUTION, Math.max(0, share + transfers));
            session.getCurrentRoundData().setPoolPayout(uuid, paid);
            Player player = Bukkit.getPlayer(uuid);
            if (previousRound == null && player != null) {
                Messages.send(player, "economy.round-share-paid",
                        "pool", String.format("%,d", pool),
                        "amount", String.format("%,d", paid));
            }
        }

        Messages.debug("ECONOMY", "Round " + session.getCurrentRound() + " (economy round " + getEconomyRound(session)
                + ") pool " + pool + " - share " + share);
    }

    /**
     * Tells a player entering a buy phase about the pool it just paid out and what they got from
     * it: their share plus the kill and assist transfers from the round just played. Transfers
     * cancel out, so everyone's amounts add up to the pool.
     */
    public static void sendRoundEarnings(GameSession session, Player player) {
        if (session.getPreviousRoundData() == null) return;

        long pool = getRoundPool(session);
        if (pool <= 0) return;

        Messages.send(player, "economy.round-money-earned",
                "pool", String.format("%,d", pool),
                "amount", String.format("%,d", session.getCurrentRoundData().getPoolPayout(player.getUniqueId())));
    }

    /**
     * Moves the kill transfer off the victim's payout at the next buy phase and onto the killer's
     * and any assisting teammates': a percentage of that buy phase's pool. Nobody's balance
     * changes during the round, and a kill never creates money. The victim can't lose more than
     * that payout; each assist takes its configured share and the killer keeps the rest.
     */
    public static void transferKillMoney(GameSession session, Player victim, Player killer) {
        Team killerTeam = session.getPlayerTeam(killer);
        if (session.getCashClashPlayer(victim.getUniqueId()) == null || killerTeam == null
                || killerTeam == session.getPlayerTeam(victim)) return;

        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        RoundData roundData = session.getCurrentRoundData();
        long nextPool = getNextRoundPool(session);
        long idealTransfer = Math.round(nextPool * killTransferPercent(session, killerTeam, nextPool) / 100.0);
        long victimPayout = standardShare(nextPool) + roundData.getPoolTransfers(victim.getUniqueId());
        long transfer = Math.max(0, Math.min(victimPayout, idealTransfer));

        List<UUID> assisters = session.getAssistTracker()
                .getRecentAttackers(victim.getUniqueId(), killer.getUniqueId(), cfg.getAssistWindowSeconds() * 1000L)
                .stream()
                .filter(killerTeam::hasPlayer)
                .toList();
        long assistShare = assisters.isEmpty() ? 0
                : Math.min((long) (transfer * cfg.getKillTransferAssistSharePercent() / 100.0), transfer / assisters.size());
        long killerShare = transfer - assistShare * assisters.size();

        roundData.addPoolTransfer(victim.getUniqueId(), -transfer);
        Messages.send(victim, "economy.kill-transfer-lost",
                "amount", String.format("%,d", transfer),
                "killer", killer.getName());

        roundData.addPoolTransfer(killer.getUniqueId(), killerShare);
        Messages.send(killer, "economy.kill-transfer-received",
                "amount", String.format("%,d", killerShare),
                "victim", victim.getName());
        session.getRewardManager().grantKillOrObjective(killer, RewardType.KILL, 0);

        for (UUID assister : assisters) {
            roundData.addPoolTransfer(assister, assistShare);
            Player assistPlayer = Bukkit.getPlayer(assister);
            if (assistPlayer != null) {
                Messages.send(assistPlayer, "economy.kill-transfer-assist",
                        "amount", String.format("%,d", assistShare),
                        "victim", victim.getName());
            }
            session.getRewardManager().grantAssist(assister, 0);
        }

        Messages.debug("ECONOMY", victim.getName() + " killed by " + killer.getName() + " - transfer " + transfer
                + " of next pool " + nextPool + " (killer " + killerShare + ", " + assisters.size() + " assist(s) at " + assistShare + ")");
    }

    /**
     * The kill-transfer rate for a kill made by {@code killerTeam}: the base rate, or a comeback
     * rate when that team is behind on this round's kills by one of the configured tiers. Both
     * teams start every round level, at half of the next pool each, so the comeback rates reset
     * every round.
     */
    private static double killTransferPercent(GameSession session, Team killerTeam, long nextPool) {
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        long killerTeamMoney = nextPool / 2 + teamTransfers(session, killerTeam);
        long otherTeamMoney = nextPool / 2 + teamTransfers(session, session.getOpposingTeam(killerTeam));
        if (otherTeamMoney <= killerTeamMoney) return cfg.getKillTransferBasePercent();

        double deficitPercent = (otherTeamMoney - killerTeamMoney) * 100.0 / otherTeamMoney;
        Map.Entry<Double, Double> tier = cfg.getComebackTransferPercents().floorEntry(deficitPercent);
        return tier != null ? tier.getValue() : cfg.getKillTransferBasePercent();
    }

    private static long teamTransfers(GameSession session, Team team) {
        RoundData roundData = session.getCurrentRoundData();
        long total = 0;
        for (UUID uuid : team.getPlayers()) {
            total += roundData.getPoolTransfers(uuid);
        }
        return total;
    }

    public static double getTransferFee(GameSession session) {
        int round = getEconomyRound(session);
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        return switch (round) {
            case 1 -> cfg.getRound1TransferFee();
            case 2, 3 -> cfg.getRound23TransferFee();
            case 4, 5 -> cfg.getRound45TransferFee();
            case 6, 7 -> cfg.getLateRoundStealPercentage();
            default -> 0.0;
        };
    }

    /**
     * The receiver gets the whole amount; the fee is charged to the sender on top of it.
     *
     * @return false if the sender can't cover the amount plus the fee
     */
    public static boolean transferMoney(CashClashPlayer sender, CashClashPlayer receiver, long amount, GameSession session) {
        long fee = (long) (amount * getTransferFee(session));
        if (!sender.canAfford(amount + fee)) {
            return false;
        }

        Messages.debug("ECONOMY", "Transfer: " + sender.getPlayer().getName() + " -> " + receiver.getPlayer().getName() + " Amount: " + amount + " Fee: " + fee);

        sender.deductCoins(amount + fee);
        receiver.addCoins(amount);

        return true;
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
