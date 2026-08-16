package me.psikuvit.cashClash.util.game.ptp;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.player.CashClashPlayer;
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
            CashClashPlayer.applyEffect(player, PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false);
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
        CashClashPlayer.removeEffect(player, PotionEffectType.GLOWING);
        CashClashPlayer.removeEffect(player, PotionEffectType.STRENGTH);
        CashClashPlayer.removeEffect(player, PotionEffectType.RESISTANCE);
        CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);
        CashClashPlayer.removeEffect(player, PotionEffectType.SLOWNESS);
    }

    /**
     * Apply the Tank buff: a Turtle Master-style Resistance + Slowness combo (rather than plain
     * Resistance), at the amplifiers configured under
     * {@code gamemodes.protect-the-president.tank-*-amplifier}.
     */
    public static void applyTankBuff(Player player) {
        if (player == null) return;
        var cfg = CashClashPlugin.getInstance().getConfigManager();
        CashClashPlayer.applyEffect(player, PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, cfg.getPTPTankResistanceAmplifier(), false, false);
        CashClashPlayer.applyEffect(player, PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, cfg.getPTPTankSlownessAmplifier(), false, false);
        Messages.debug("[PTP] Applied Turtle Master buff (Resistance/Slowness) to president: " + player.getName());
    }

    /**
     * Apply the HP buff: a one-time instant heal (matching what an actual Instant Health potion
     * does) rather than a lingering max-health increase, at the amount configured under
     * {@code gamemodes.protect-the-president.hp-heal-amount}.
     */
    public static void applyHpBuff(Player player) {
        if (player == null) return;
        double healAmount = CashClashPlugin.getInstance().getConfigManager().getPTPHpHealAmount();
        CashClashPlayer.heal(player, healAmount);
        Messages.debug("[PTP] Applied instant heal (+" + healAmount + ") to president: " + player.getName());
    }

    /**
     * Apply a specific potion effect to a president
     *
     * @param player The president player
     * @param effect The potion effect to apply
     */
    public static void applyPotionEffect(Player player, PotionEffectType effect) {
        if (player != null && effect != null) {
            CashClashPlayer.applyEffect(player, effect, PotionEffect.INFINITE_DURATION, 0, false, false);
            Messages.debug("[PTP] Applied " + effect.getKey().getKey() + " to president: " + player.getName());
        }
    }

    /**
     * Remove strength effect from a player
     *
     * @param player The player
     */
    public static void removeStrengthEffect(Player player) {
        if (player != null) {
            CashClashPlayer.removeEffect(player, PotionEffectType.STRENGTH);
        }
    }

    /**
     * Remove resistance effect from a player
     *
     * @param player The player
     */
    public static void removeResistanceEffect(Player player) {
        if (player != null) {
            CashClashPlayer.removeEffect(player, PotionEffectType.RESISTANCE);
        }
    }

    /**
     * Remove speed effect from a player
     *
     * @param player The player
     */
    public static void removeSpeedEffect(Player player) {
        if (player != null) {
            CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);
        }
    }

    /**
     * Remove glow effect from a player
     *
     * @param player The player
     */
    public static void removeGlowEffect(Player player) {
        if (player != null) {
            CashClashPlayer.removeEffect(player, PotionEffectType.GLOWING);
        }
    }
}

