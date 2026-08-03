package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Overdrive Potion: drinking grants total invincibility plus a speed boost for
 * the configured duration. The speed boost is a MOVEMENT_SPEED attribute modifier
 * so it keeps working while the player is immune to potion effects. Right-clicking
 * again while active cancels the invincibility early but leaves the speed running
 * until the original duration elapses.
 */
public class OverdriveHandler extends CustomItemHandler {

    // Overdrive Potion - invincibility window + pulsing aura task (speed modifier has its own
    // duration; cancelling early only drops the invincibility, not the speed boost)
    private final Set<UUID> overdriveInvincible;
    private final Map<UUID, BukkitTask> overdrivePulseTasks;
    private static final NamespacedKey OVERDRIVE_SPEED_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "overdrive_speed");

    public OverdriveHandler(CustomItemManager manager) {
        super(manager);
        this.overdriveInvincible = new HashSet<>();
        this.overdrivePulseTasks = new HashMap<>();
    }

    public boolean isOverdriveInvincible(UUID uuid) {
        return overdriveInvincible.contains(uuid);
    }

    /**
     * Drinks the Overdrive Potion: grants total invincibility + a speed boost for the
     * configured duration. Speed uses a MOVEMENT_SPEED AttributeModifier so it keeps working
     * even though the player is immune to potion effects while invincible.
     */
    public void useOverdrivePotion(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        consumeItem(player, item);

        overdriveInvincible.add(uuid);
        applyOverdriveSpeed(player);

        int seconds = cfg.getOverdriveInvincibilitySeconds();

        Messages.send(player, "customitem.overdrive-activated");
        SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);

        // Purple engulf on activation + pulsing aura while active
        ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 40, 220), 1.6f, 40, 0.6);

        BukkitTask pulseTask = SchedulerUtils.runTaskTimer(() -> {
            if (!player.isOnline() || !overdriveInvincible.contains(uuid)) return;
            ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(165, 70, 230), 1.2f, 12, 0.4);
        }, 5L, 5L);
        overdrivePulseTasks.put(uuid, pulseTask);

        SchedulerUtils.runTaskLater(() -> endOverdrive(player), seconds * 20L);
    }

    /**
     * Right-clicking again while active cancels the invincibility early; the speed boost keeps
     * running until the original duration elapses (endOverdrive handles its removal).
     */
    public void cancelOverdriveEarly(Player player) {
        UUID uuid = player.getUniqueId();
        if (!overdriveInvincible.remove(uuid)) return;

        BukkitTask task = overdrivePulseTasks.remove(uuid);
        if (task != null) task.cancel();

        ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 40, 220), 1.2f, 20, 0.4);
        Messages.send(player, "customitem.overdrive-cancelled");
        SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
    }

    private void endOverdrive(Player player) {
        UUID uuid = player.getUniqueId();
        overdriveInvincible.remove(uuid);

        BukkitTask task = overdrivePulseTasks.remove(uuid);
        if (task != null) task.cancel();

        removeOverdriveSpeed(player);

        if (player.isOnline()) {
            Messages.send(player, "customitem.overdrive-ended");
        }
    }

    private void applyOverdriveSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double boost = cfg.getOverdriveSpeedPercent() / 100.0;
        speed.addModifier(new AttributeModifier(OVERDRIVE_SPEED_KEY, boost, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeOverdriveSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(OVERDRIVE_SPEED_KEY);
    }

    @Override
    public void cleanup() {
        overdrivePulseTasks.forEach((uuid, task) -> {
            task.cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removeOverdriveSpeed(player);
        });
        overdrivePulseTasks.clear();
        overdriveInvincible.clear();
    }
}
