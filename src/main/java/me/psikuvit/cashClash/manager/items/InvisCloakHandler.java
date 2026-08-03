package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invis Cloak: toggled invisibility that drains coins per second while active.
 * The player's armor (and off-hand shield) is stashed while invisible so nothing
 * gives them away, and restored on deactivate or death. Each activation consumes
 * one of the 5 uses. Handles the shopping-phase force-off on its own.
 */
public class InvisCloakHandler extends CustomItemHandler {

    private final Map<UUID, Integer> invisCloakUsesRemaining;
    private final Set<UUID> invisCloakActive;
    private final Map<UUID, BukkitTask> invisCloakTasks;
    private final Map<UUID, List<ItemStack>> invisCloakStoredArmor;

    public InvisCloakHandler(CustomItemManager manager) {
        super(manager);
        this.invisCloakUsesRemaining = new HashMap<>();
        this.invisCloakActive = new HashSet<>();
        this.invisCloakTasks = new HashMap<>();
        this.invisCloakStoredArmor = new HashMap<>();
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

            // Store and hide armor
            ItemStack[] currentArmor = player.getInventory().getArmorContents();
            List<ItemStack> armorCopy = new ArrayList<>();
            for (ItemStack stack : currentArmor) {
                armorCopy.add(stack != null ? stack.clone() : null);
            }
            if (player.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
                armorCopy.add(player.getInventory().getItemInOffHand().clone());
            }
            invisCloakStoredArmor.put(uuid, armorCopy);
            player.getInventory().setArmorContents(new ItemStack[4]); // Clear visible armor
            player.getInventory().setItemInOffHand(null); // Clear shield if any

            CashClashPlayer.applyEffect(player, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0);

            // Remove all arrows from the player (arrows stuck in them)
            removeArrowsFromPlayer(player);

            Messages.send(player, "customitem.invis-activated");
            int costPerSecond = cfg.getInvisCloakCostPerSecond();
            Messages.send(player, "customitem.invis-cost-per-second", "cost", String.valueOf(costPerSecond));
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

            GameSession session = GameManager.getInstance().getPlayerSession(player);
            CashClashPlayer ccp = session != null ? session.getCashClashPlayer(uuid) : null;

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

            // Restore armor
            List<ItemStack> storedArmor = invisCloakStoredArmor.remove(uuid);
            if (storedArmor != null && storedArmor.size() >= 4) {
                // Restore armor contents (first 4 items are helmet, chestplate, leggings, boots)
                ItemStack[] armorContents = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    armorContents[i] = storedArmor.get(i);
                }
                player.getInventory().setArmorContents(armorContents);

                // Check if there's a 5th item (shield in offhand)
                if (storedArmor.size() > 4 && storedArmor.get(4) != null) {
                    player.getInventory().setItemInOffHand(storedArmor.get(4));
                }
            }

            BukkitTask task = invisCloakTasks.remove(uuid);
            if (task != null) task.cancel();

            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, cfg.getInvisCloakCooldown());

            Messages.send(player, "customitem.invis-deactivated");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
        }
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
     * Clears invisibility cloak state on death and restores armor.
     * The armor was hidden when invis was activated, so we need to restore it.
     */
    public void clearInvisCloakOnDeath(Player player) {
        UUID uuid = player.getUniqueId();

        if (!invisCloakActive.contains(uuid)) return;

        invisCloakActive.remove(uuid);
        CashClashPlayer.removeEffect(player, PotionEffectType.INVISIBILITY);

        // Restore armor that was hidden during invisibility
        List<ItemStack> storedArmor = invisCloakStoredArmor.remove(uuid);
        if (storedArmor != null && storedArmor.size() >= 4) {
            // Restore armor contents (first 4 items are helmet, chestplate, leggings, boots)
            ItemStack[] armorContents = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                armorContents[i] = storedArmor.get(i);
            }
            player.getInventory().setArmorContents(armorContents);

            // Check if there's a 5th item (shield in offhand)
            if (storedArmor.size() > 4 && storedArmor.get(4) != null) {
                player.getInventory().setItemInOffHand(storedArmor.get(4));
            }
        }

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
        invisCloakActive.clear();
        invisCloakStoredArmor.clear();
    }
}
