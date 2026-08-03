package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Base class for every custom item's behaviour. Each item gets its own handler
 * extending this class, so no single manager accumulates every item's state and
 * logic. {@link CustomItemManager} owns one instance of each handler and routes
 * public calls to the right one.
 *
 * <p>The base class exposes the shared dependencies every item needs: the global
 * cooldown manager, the items config, the armor hooks, plus a few small helpers
 * (consuming an item, resolving max health, team checks). Per-item state and
 * cleanup live in each subclass.</p>
 */
public abstract class CustomItemHandler {

    protected final CustomItemManager manager;
    protected final CooldownManager cooldownManager;
    protected final ItemsConfig cfg;
    protected final CustomArmorManager armorManager;

    protected CustomItemHandler(CustomItemManager manager) {
        this.manager = manager;
        this.cooldownManager = manager.getCooldownManager();
        this.cfg = manager.getCfg();
        this.armorManager = manager.getArmorManager();
    }

    /**
     * Called by {@link CustomItemManager#cleanup()} when a game ends - cancels
     * scheduled tasks, removes placed blocks/entities and clears per-item state.
     */
    public abstract void cleanup();

    /**
     * Removes one unit of the given item; if it was the last one, clears the slot
     * it sat in. The item is expected to be in the player's main hand.
     */
    protected void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * @return the target's max health through the centralized health system, 20.0
     *         when the player has no session/record to look up
     */
    protected double getMaxHealth(Player target) {
        CashClashPlayer ccp = CashClashPlayer.from(target);
        return ccp != null ? ccp.getMaxHealth() : 20.0;
    }

    protected boolean isSameTeam(GameSession session, int teamNumber, Player target) {
        if (session == null) return false;
        Team targetTeam = session.getPlayerTeam(target);
        return targetTeam != null && targetTeam.getTeamNumber() == teamNumber;
    }
}
