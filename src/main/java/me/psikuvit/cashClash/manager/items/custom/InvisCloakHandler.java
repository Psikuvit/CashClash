package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invis Cloak: toggled invisibility that drains coins per second while active.
 * The player's armor and off-hand item are stashed via CashClashPlayer#hideInventory while
 * invisible so nothing gives them away, and restored on deactivate or death (the main hand
 * stays as-is - it holds the cloak itself, needed to right-click deactivate). Each activation
 * consumes one of the 5 uses. Handles the shopping-phase force-off on its own.
 */
public class InvisCloakHandler extends CustomItemHandler {

    private final Map<UUID, Integer> invisCloakUsesRemaining;
    private final Set<UUID> invisCloakActive;
    private final Map<UUID, BukkitTask> invisCloakTasks;

    public InvisCloakHandler(CustomItemManager manager) {
        super(manager);
        this.invisCloakUsesRemaining = new HashMap<>();
        this.invisCloakActive = new HashSet<>();
        this.invisCloakTasks = new HashMap<>();
    }

    public void toggleInvisCloak(Player player, boolean turnOn) {
        UUID uuid = player.getUniqueId();

        if (turnOn && !invisCloakActive.contains(uuid)) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.INVIS_CLOAK)) {
                long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK);
                Messages.send(player, "customitem.invis-cooldown", "remaining", String.valueOf(remaining));
                return;
            }

            int uses = invisCloakUsesRemaining.getOrDefault(uuid, 5);
            if (uses <= 0) {
                Messages.send(player, "customitem.no-uses-remaining");
                return;
            }

            invisCloakActive.add(uuid);
            invisCloakUsesRemaining.put(uuid, uses - 1);

            GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
            CashClashPlayer ccp = session != null ? session.getCashClashPlayer(uuid) : null;

            // Hide worn armor + off-hand - a vanilla Invisibility effect alone still shows
            // equipped/held items floating in place. The main-hand item stays untouched: it's
            // the cloak itself, and the right-click-to-deactivate flow needs it in hand.
            if (ccp != null) ccp.hideInventory(false);

            CashClashPlayer.applyEffect(player, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0);

            // Remove all arrows from the player (arrows stuck in them)
            removeArrowsFromPlayer(player);

            Messages.send(player, "customitem.invis-activated");
            int costPerSecond = cfg.getInvisCloakCostPerSecond();
            Messages.send(player, "customitem.invis-cost-per-second", "cost", String.valueOf(costPerSecond));
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);
            playInvisToggleEffect(player);

            BukkitTask drainTask = SchedulerUtils.runTaskTimer(() -> {
                if (!invisCloakActive.contains(uuid)) return;

                if (ccp != null && ccp.getCoins() >= costPerSecond) {
                    ccp.deductCoins(costPerSecond);
                } else {
                    toggleInvisCloak(player, false);
                    Messages.send(player, "customitem.invis-out-of-coins");
                }
            }, 20L, 20L);

            invisCloakTasks.put(uuid, drainTask);

        } else if (!turnOn && invisCloakActive.contains(uuid)) {
            invisCloakActive.remove(uuid);
            CashClashPlayer.removeEffect(player, PotionEffectType.INVISIBILITY);
            CashClashPlayer.restoreInventory(player);

            BukkitTask task = invisCloakTasks.remove(uuid);
            if (task != null) task.cancel();

            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, cfg.getInvisCloakCooldown());

            Messages.send(player, "customitem.invis-deactivated");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
            playInvisToggleEffect(player);
        }
    }

    /**
     * Quick gray dust burst around the player when the cloak toggles on or off.
     */
    private void playInvisToggleEffect(Player player) {
        Location center = player.getLocation().clone().add(0, 1.0, 0);
        ParticleUtils.spawnDust(center, Color.fromRGB(150, 150, 150), 1.2f, 25, 0.4, 0.6, 0.4);
    }

    /**
     * Handles right-click with invis cloak - toggles invisibility.
     */
    public void handleInvisCloakRightClick(Player player) {
        UUID uuid = player.getUniqueId();

        // If already active, turn off
        if (invisCloakActive.contains(uuid)) {
            toggleInvisCloak(player, false);
            return;
        }

        // Otherwise, turn on
        toggleInvisCloak(player, true);
    }

    public boolean isInvisActive(UUID uuid) {
        return invisCloakActive.contains(uuid);
    }

    /**
     * Clears invisibility cloak state on death and restores the player's equipment.
     * It was hidden when invis was activated, so we need to restore it.
     */
    public void clearInvisCloakOnDeath(Player player) {
        UUID uuid = player.getUniqueId();

        if (!invisCloakActive.contains(uuid)) return;

        invisCloakActive.remove(uuid);
        CashClashPlayer.removeEffect(player, PotionEffectType.INVISIBILITY);
        CashClashPlayer.restoreInventory(player);

        // Cancel the drain task
        BukkitTask task = invisCloakTasks.remove(uuid);
        if (task != null) task.cancel();

        // Reset cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, cfg.getInvisCloakCooldown());
    }

    /**
     * Remove all arrows from a player's body
     */
    private void removeArrowsFromPlayer(Player player) {
        player.setArrowsInBody(0);
    }

    /**
     * Disable all active invisibility cloaks - used when shopping phase starts
     */
    public void disableAll() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player player : online) {
            UUID uuid = player.getUniqueId();
            if (invisCloakActive.contains(uuid)) {
                toggleInvisCloak(player, false);
                Messages.send(player, "customitem.invis-disabled-shopping");
            }
        }
    }

    @Override
    public void cleanup() {
        invisCloakTasks.values().forEach(BukkitTask::cancel);
        invisCloakTasks.clear();

        // Restore any still-hidden equipment before the session (and its CashClashPlayer
        // instances, which hold the stashed items) goes away - otherwise an abrupt session
        // end while a player is invisible would lose their gear permanently.
        for (UUID uuid : invisCloakActive) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                CashClashPlayer.removeEffect(player, PotionEffectType.INVISIBILITY);
                CashClashPlayer.restoreInventory(player);
            }
        }
        invisCloakActive.clear();
    }
}
