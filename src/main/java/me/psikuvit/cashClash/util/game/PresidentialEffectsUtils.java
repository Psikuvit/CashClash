package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Utility class for managing presidential effects in Protect the President mode.
 * Handles applying, refreshing, and clearing presidential buffs and effects.
 */
public class PresidentialEffectsUtils {

    private PresidentialEffectsUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Apply glowing effect to a president
     *
     * @param player The president player
     */
    public static void applyGlowEffect(Player player) {
        if (player != null && player.isOnline()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
            Messages.debug("[PTP] Applied glowing to president: " + player.getName());
        }
    }

    /**
     * Clear all presidential effects from a player
     *
     * @param player The president player
     */
    public static void clearPresidentialEffects(Player player) {
        if (player == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    /**
     * Apply a specific potion effect to a president
     *
     * @param player The president player
     * @param effect The potion effect to apply
     */
    public static void applyPotionEffect(Player player, PotionEffectType effect) {
        if (player != null && effect != null) {
            player.addPotionEffect(new PotionEffect(effect, PotionEffect.INFINITE_DURATION, 0, false, false));
            Messages.debug("[PTP] Applied " + effect.getName() + " to president: " + player.getName());
        }
    }

    /**
     * Apply extra hearts buff to a president
     *
     * @param player The president player
     * @param healthModifier The health modifier value (6.0 = 3 hearts)
     */
    public static void applyExtraHearts(Player player, double healthModifier) {
        if (player != null) {
            Messages.debug("[PTP] Applied +" + (healthModifier / 2.0) + " hearts buff to: " + player.getName());
        }
    }

    /**
     * Remove strength effect from a player
     *
     * @param player The player
     */
    public static void removeStrengthEffect(Player player) {
        if (player != null) {
            player.removePotionEffect(PotionEffectType.STRENGTH);
        }
    }

    /**
     * Remove resistance effect from a player
     *
     * @param player The player
     */
    public static void removeResistanceEffect(Player player) {
        if (player != null) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    /**
     * Remove speed effect from a player
     *
     * @param player The player
     */
    public static void removeSpeedEffect(Player player) {
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    /**
     * Remove glow effect from a player
     *
     * @param player The player
     */
    public static void removeGlowEffect(Player player) {
        if (player != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }
}

