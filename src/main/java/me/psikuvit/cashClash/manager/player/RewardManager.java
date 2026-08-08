package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.armor.InvestorSetHandler;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.RewardType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Single funnel for every achievement-style coin reward (kills, bonuses, objective
 * completions, round-end distribution, loss-streak comeback, weapon rewards, supply drops).
 * Shop refunds/purchases, money transfers, admin coin grants, and investment payouts are
 * transactions rather than earned rewards and don't go through here.
 *
 * <p>Centralizes three things that used to be duplicated at each call site: the coin
 * multiplier hook (currently a pass-through, but a real extension point for a future
 * global/per-round multiplier), the {@code messages.yml} key to announce a reward with (via
 * {@link RewardType}), and the Investor's Set kill/objective bonus trigger.</p>
 */
public class RewardManager {

    private final GameSession session;

    public RewardManager(GameSession session) {
        this.session = session;
    }

    /**
     * Grant a reward: applies the multiplier, adds the coins, and sends the type's
     * {@code messages.yml} announcement if it has one.
     *
     * @param player the recipient
     * @param type the reward type (resolves the message key)
     * @param amount the base amount, before the multiplier
     * @param placeholders alternating key/value pairs forwarded to {@link Messages#send}
     */
    public void grant(Player player, RewardType type, long amount, String... placeholders) {
        if (player == null) return;
        grant(player.getUniqueId(), type, amount, placeholders);
    }

    /**
     * Same as {@link #grant(Player, RewardType, long, String...)}, but works from a UUID so
     * a disconnected-but-still-in-session player still gets credited (e.g. round-end pool
     * distribution) - the message is simply skipped if they're not online to see it.
     */
    public void grant(UUID playerUuid, RewardType type, long amount, String... placeholders) {
        CashClashPlayer ccp = session.getCashClashPlayer(playerUuid);
        if (ccp == null) return;

        long finalAmount = applyMultiplier(type, amount);
        ccp.addCoins(finalAmount);

        if (type.getMessageKey() == null) return;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            Messages.send(player, type.getMessageKey(), placeholders);
        }
    }

    /**
     * Kill / objective completion: grants {@code amount} (if positive, via {@link #grant})
     * and, for investor-eligible types, always runs the Investor's Set bonus check -
     * replaces direct {@code InvestorSetHandler} calls from gamemode/listener code.
     */
    public void grantKillOrObjective(Player player, RewardType type, long amount) {
        if (player == null) return;

        if (amount > 0) {
            grant(player, type, amount);
        }

        if (type.isInvestorEligible()) {
            CashClashPlugin.getInstance().getCustomArmorManager().getHandler(InvestorSetHandler.class)
                    .onInvestorReward(player, session);
        }
    }

    /**
     * Extension point for a future global/per-round coin multiplier - currently a
     * pass-through, kept as its own method so the hook exists in one obvious place.
     */
    private long applyMultiplier(RewardType type, long amount) {
        return amount;
    }
}
