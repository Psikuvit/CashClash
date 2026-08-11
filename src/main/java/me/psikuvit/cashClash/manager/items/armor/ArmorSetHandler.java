package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for per-set armor handlers.
 */
public abstract class ArmorSetHandler {

    protected final CustomArmorManager manager;
    protected final ItemsConfig cfg;
    protected final CooldownManager cooldownManager;

    protected ArmorSetHandler(CustomArmorManager manager) {
        this.manager = manager;
        this.cfg = manager.getCfg();
        this.cooldownManager = manager.getCooldownManager();
    }

    protected List<CustomArmorItem> getEquippedCustomArmor(Player player) {
        List<CustomArmorItem> found = new ArrayList<>();
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is == null) continue;

            CustomArmorItem armor = PDCDetection.getCustomArmor(is);
            if (armor == null) continue;
            found.add(armor);
        }
        return found;
    }

    /**
     * Check if player is silenced (carrying enemy flag in CTF or dead)
     */
    protected boolean isSilenced(Player player) {
        // Dead players are silenced for all items
        if (CashClashPlayer.isPlayerDead(player)) {
            return true;
        }

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }

    /**
     * Whether the player is in a phase where custom armor abilities must not fire - the
     * shopping phase, or a sequence that mirrors its restrictions.
     */
    protected boolean isInShoppingPhase(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        return session != null && (session.getState() == GameState.SHOPPING || session.isActionsRestricted());
    }

    /**
     * Check if the player's melee attack is fully charged, against the configurable
     * {@code combat.fully-charged-threshold}.
     */
    protected boolean isFullyChargedMelee(Player attacker) {
        // Bukkit exposes attack cooldown directly
        try {
            return attacker.getAttackCooldown() >= cfg.getFullyChargedThreshold();
        } catch (NoSuchMethodError ignored) {
            return true;
        }
    }

    public abstract void cleanup();

    public abstract void resetRoundTracking();
}
