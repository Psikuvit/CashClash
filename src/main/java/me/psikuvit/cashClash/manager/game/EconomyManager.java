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
     * The fixed money pool of the session's current round, or 0 if that round has none.
     */
    public static long getRoundPool(GameSession session) {
        return CashClashPlugin.getInstance().getConfigManager().getRoundPool(session.getCurrentRound());
    }

    /**
     * Pays every player an equal share of the current round's pool at the start of its buy
     * phase, on top of whatever they have left over. Only the first buy phase announces the
     * share; later ones show the previous round's earnings instead (see {@link #sendRoundEarnings}).
     */
    public static void payRoundShare(GameSession session) {
        long pool = getRoundPool(session);
        if (pool <= 0) return;

        long share = pool / Math.max(1, CashClashPlugin.getInstance().getConfigManager().getRoundPoolSplit());
        boolean announceShare = session.getPreviousRoundData() == null;
        for (UUID uuid : session.getPlayers()) {
            long paid = session.getRewardManager().grant(uuid, RewardType.ROUND_DISTRIBUTION, share);
            session.getCurrentRoundData().addPoolEarnings(uuid, paid);
            Player player = Bukkit.getPlayer(uuid);
            if (announceShare && player != null) {
                Messages.send(player, "economy.round-share-paid",
                        "pool", String.format("%,d", pool),
                        "amount", String.format("%,d", paid));
            }
        }

        Messages.debug("ECONOMY", "Round " + session.getCurrentRound() + " pool " + pool + " - paid " + share + " to each player");
    }

    /**
     * Tells a player entering a buy phase what they ended up with from the previous round's
     * pool: their share plus kill and assist transfers received, minus transfers lost on death.
     */
    public static void sendRoundEarnings(GameSession session, Player player) {
        RoundData previousRound = session.getPreviousRoundData();
        if (previousRound == null) return;

        long pool = CashClashPlugin.getInstance().getConfigManager().getRoundPool(session.getCurrentRound() - 1);
        if (pool <= 0) return;

        Messages.send(player, "economy.round-money-earned",
                "pool", String.format("%,d", pool),
                "amount", String.format("%,d", previousRound.getPoolEarnings(player.getUniqueId())));
    }

    /**
     * Moves the kill transfer from the victim to the killer and any teammates who assisted. The
     * whole amount comes out of the victim's balance (capped at what they have), so a kill never
     * creates money; each assist takes its configured share and the killer keeps the rest.
     */
    public static void transferKillMoney(GameSession session, Player victim, Player killer) {
        CashClashPlayer victimCcp = session.getCashClashPlayer(victim.getUniqueId());
        Team killerTeam = session.getPlayerTeam(killer);
        if (victimCcp == null || killerTeam == null || killerTeam == session.getPlayerTeam(victim)) return;

        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        long idealTransfer = Math.round(getRoundPool(session) * killTransferPercent(session, killerTeam) / 100.0);
        long transfer = Math.max(0, Math.min(victimCcp.getCoins(), idealTransfer));

        List<UUID> assisters = session.getAssistTracker()
                .getRecentAttackers(victim.getUniqueId(), killer.getUniqueId(), cfg.getAssistWindowSeconds() * 1000L)
                .stream()
                .filter(killerTeam::hasPlayer)
                .toList();
        long assistShare = assisters.isEmpty() ? 0
                : Math.min((long) (transfer * cfg.getKillTransferAssistSharePercent() / 100.0), transfer / assisters.size());
        long killerShare = transfer - assistShare * assisters.size();

        RoundData roundData = session.getCurrentRoundData();
        if (transfer > 0) {
            victimCcp.deductCoins(transfer);
            roundData.addPoolEarnings(victim.getUniqueId(), -transfer);
            Messages.send(victim, "economy.kill-transfer-lost",
                    "amount", String.format("%,d", transfer),
                    "killer", killer.getName());
        }

        session.getRewardManager().grantKillOrObjective(killer, RewardType.KILL, killerShare,
                "amount", String.format("%,d", killerShare),
                "victim", victim.getName());
        roundData.addPoolEarnings(killer.getUniqueId(), killerShare);

        for (UUID assister : assisters) {
            session.getRewardManager().grantAssist(assister, assistShare,
                    "amount", String.format("%,d", assistShare),
                    "victim", victim.getName());
            roundData.addPoolEarnings(assister, assistShare);
        }

        Messages.debug("ECONOMY", victim.getName() + " killed by " + killer.getName() + " - transfer " + transfer
                + " (killer " + killerShare + ", " + assisters.size() + " assist(s) at " + assistShare + ")");
    }

    /**
     * The kill-transfer rate for a kill made by {@code killerTeam}: the base rate, or a comeback
     * rate when that team is behind the other team's total money by one of the configured tiers.
     */
    private static double killTransferPercent(GameSession session, Team killerTeam) {
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        long killerTeamMoney = teamCoins(session, killerTeam);
        long otherTeamMoney = teamCoins(session, session.getOpposingTeam(killerTeam));
        if (otherTeamMoney <= killerTeamMoney) return cfg.getKillTransferBasePercent();

        double deficitPercent = (otherTeamMoney - killerTeamMoney) * 100.0 / otherTeamMoney;
        Map.Entry<Double, Double> tier = cfg.getComebackTransferPercents().floorEntry(deficitPercent);
        return tier != null ? tier.getValue() : cfg.getKillTransferBasePercent();
    }

    private static long teamCoins(GameSession session, Team team) {
        long total = 0;
        for (UUID uuid : team.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) total += ccp.getCoins();
        }
        return total;
    }

    public static double getTransferFee(GameSession session) {
        int round = session.getCurrentRound();
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
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
