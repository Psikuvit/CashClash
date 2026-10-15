package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.game.AbilityTargets;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

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

    /**
     * Whether a player is in a match. Outside one - the hub, the practice range - there are no
     * teams: an item only helps its own user and only hits practice-range mobs, never another
     * player, so nobody can grief the hub with it.
     */
    protected static boolean inGame(Player player) {
        return AbilityTargets.inGame(player);
    }

    /**
     * Whether a world belongs to a running match - for effects that can go off after their user
     * has left.
     */
    protected static boolean isGameWorld(World world) {
        return world != null && CashClashPlugin.getInstance().getGameManager().getActiveSessions().stream()
                .anyMatch(session -> world.equals(session.getGameWorld()));
    }

    /**
     * Whether an ability can hit an entity: in a game any player (each item narrows that down by
     * team itself), outside one only a practice-range mob.
     */
    protected static boolean canHit(boolean inGame, Entity entity) {
        return inGame ? entity instanceof Player : PDCDetection.isPracticeMob(entity);
    }

    /**
     * A potion effect on an ability's target: through {@link CashClashPlayer} for a player, so it's
     * tracked, and directly for a practice mob.
     */
    protected static void applyEffect(LivingEntity target, PotionEffectType type, int durationTicks, int amplifier,
                                      boolean ambient, boolean particles) {
        AbilityTargets.applyEffect(target, type, durationTicks, amplifier, ambient, particles);
    }
}
