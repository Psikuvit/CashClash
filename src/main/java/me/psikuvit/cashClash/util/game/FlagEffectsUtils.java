package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.gamemode.impl.FlagState;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for managing visual effects related to flags in CTF
 * Includes glow effects for flag carriers and particle effects
 */
public class FlagEffectsUtils {

    private static final int GLOW_INTERVAL_TICKS = 100; // 5 seconds
    private static final int GLOW_DURATION_TICKS = 10;
    private static final int GLOW_AMPLIFIER = 0;

    private FlagEffectsUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Start a task that applies glow effect to flag carriers every 5 seconds
     *
     * @param flagStatesSupplier Supplier for current flag states
     * @return The BukkitTask managing the glow effect
     */
    public static BukkitTask startCarrierGlowEffectTask(Supplier<Map<Integer, FlagState>> flagStatesSupplier) {
        return SchedulerUtils.runTaskTimer(() -> applyGlowToActiveCarriers(flagStatesSupplier.get()), 0, GLOW_INTERVAL_TICKS);
    }

    /**
     * Apply glow effect to all current flag carriers
     *
     * @param flagStates Map of flag states
     */
    public static void applyGlowToActiveCarriers(Map<Integer, FlagState> flagStates) {
        for (FlagState flag : flagStates.values()) {
            applyGlowIfCarrying(flag);
        }
    }

    /**
     * Apply glow effect to a flag carrier
     *
     * @param flag The flag state to check
     */
    public static void applyGlowIfCarrying(FlagState flag) {
        if (flag != null && flag.isHeld()) {
            Player carrier = Bukkit.getPlayer(flag.holder());
            if (carrier != null && carrier.isOnline()) {
                carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOW_DURATION_TICKS, GLOW_AMPLIFIER, false, false));
            }
        }
    }

    /**
     * Restore silenced items for all players in a session
     *
     * @param playerUuids Collection of player UUIDs
     * @param silencedChecker Function to check if player is silenced
     */
    public static void restoreSilencedItemsForAll(Iterable<UUID> playerUuids, Function<UUID, Boolean> silencedChecker) {
        for (UUID uuid : playerUuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ItemUtils.updateSilencedItemDisplay(player, silencedChecker.apply(uuid));
            }
        }
    }
}




