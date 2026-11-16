package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Scales Speed I by {@code speed-one-strength}: vanilla's +20% movement speed becomes +20% times
 * the strength. The effect keeps its own attribute modifier; a second one on top, redone
 * whenever a player's Speed changes, multiplies the total up or down to match. Speed II and up
 * are left alone.
 */
public class SpeedStrengthListener implements Listener {

    private static final double VANILLA_SPEED_PER_LEVEL = 0.2;

    private final ConfigManager configManager;

    public SpeedStrengthListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpeedChanged(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!PotionEffectType.SPEED.equals(event.getModifiedType())) return;
        SchedulerUtils.runTask(() -> update(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    private void update(Player player) {
        if (!player.isOnline()) return;
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(Keys.SPEED_ONE_STRENGTH);

        PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
        if (effect == null || effect.getAmplifier() != 0) return;

        double scale = (1 + VANILLA_SPEED_PER_LEVEL * configManager.getSpeedOneStrength()) / (1 + VANILLA_SPEED_PER_LEVEL) - 1;
        if (scale == 0) return;
        speed.addModifier(new AttributeModifier(Keys.SPEED_ONE_STRENGTH, scale,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }
}
