package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing extra heart timer displays in Sudden Death
 */
public class HeartTimerDisplayUtils {

    private static final long HEART_DURATION_MS = 45 * 1000; // 45 seconds

    private HeartTimerDisplayUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Start a task that displays heart timer countdowns to players
     *
     * @param playerHeartTimestamps Map tracking when each player received their heart
     * @return The BukkitTask managing the display
     */
    public static BukkitTask startHeartTimerDisplayTask(Map<UUID, Long> playerHeartTimestamps) {
        return SchedulerUtils.runTaskTimer(() -> updateHeartTimerDisplay(playerHeartTimestamps), 0, 10);
    }

    /**
     * Update heart timer display for all players with active hearts
     *
     * @param playerHeartTimestamps Map of UUID -> heart timestamp
     */
    public static void updateHeartTimerDisplay(Map<UUID, Long> playerHeartTimestamps) {
        long now = System.currentTimeMillis();

        // Use a copy of keys to avoid concurrent modification
        for (UUID playerUuid : new HashSet<>(playerHeartTimestamps.keySet())) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                playerHeartTimestamps.remove(playerUuid);
                continue;
            }

            Long heartTime = playerHeartTimestamps.get(playerUuid);
            if (heartTime == null) {
                continue;
            }

            long elapsed = now - heartTime;
            long remaining = Math.max(0, HEART_DURATION_MS - elapsed);
            long secondsRemaining = remaining / 1000 + (remaining % 1000 > 0 ? 1 : 0);

            if (remaining > 0) {
                // Heart is still active - show countdown
                String timerMsg = "<red>❤ Extra Heart expires in: <gold>" + secondsRemaining + "s</gold></red>";
                ActionBarQueue.get().startDisplay(player, timerMsg, 3, 40);
            } else {
                // Heart expired - remove from tracking
                playerHeartTimestamps.remove(playerUuid);
            }
        }
    }

    /**
     * Record that a player received an extra heart bonus
     *
     * @param playerUuid The player's UUID
     * @param playerHeartTimestamps Map to track heart timestamps
     */
    public static void recordHeartBonus(UUID playerUuid, Map<UUID, Long> playerHeartTimestamps) {
        playerHeartTimestamps.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Get remaining heart duration for a player
     *
     * @param playerUuid The player's UUID
     * @param playerHeartTimestamps Map of heart timestamps
     * @return Remaining duration in milliseconds, or 0 if no active heart
     */
    public static long getHeartTimeRemaining(UUID playerUuid, Map<UUID, Long> playerHeartTimestamps) {
        Long heartTime = playerHeartTimestamps.get(playerUuid);
        if (heartTime == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - heartTime;
        return Math.max(0, HEART_DURATION_MS - elapsed);
    }

    /**
     * Clear all heart timers
     *
     * @param playerHeartTimestamps Map to clear
     */
    public static void clearAllHeartTimers(Map<UUID, Long> playerHeartTimestamps) {
        playerHeartTimestamps.clear();
    }
}

