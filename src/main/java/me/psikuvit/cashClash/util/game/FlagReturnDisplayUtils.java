package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing flag return timers and their display
 */
public class FlagReturnDisplayUtils {

    private FlagReturnDisplayUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Schedule a display timer that shows countdown to all players
     *
     * @param teamNumber The team number (1=Red, 2=Blue)
     * @param flagReturnExpiry Map keeping track of return expiry times
     * @param playerUuids Collection of player UUIDs to display to
     * @return The BukkitTask for the display timer
     */
    public static BukkitTask scheduleFlagReturnDisplayTimer(
            int teamNumber,
            Map<Integer, Long> flagReturnExpiry,
            Collection<UUID> playerUuids) {

        return SchedulerUtils.runTaskTimer(() -> updateFlagReturnDisplay(teamNumber, flagReturnExpiry, playerUuids), 0, 20L);
    }

    /**
     * Update and broadcast the flag return display to all players
     *
     * @param teamNumber The team number
     * @param flagReturnExpiry Map of expiry times
     * @param playerUuids Collection of player UUIDs
     */
    public static void updateFlagReturnDisplay(
            int teamNumber,
            Map<Integer, Long> flagReturnExpiry,
            Collection<UUID> playerUuids) {

        Long expiry = flagReturnExpiry.get(teamNumber);
        if (expiry == null) {
            return;
        }

        long remainingMs = Math.max(0L, expiry - System.currentTimeMillis());
        if (remainingMs == 0L) {
            return;
        }

        long secondsRemaining = remainingMs / 1000L + (remainingMs % 1000L > 0 ? 1 : 0);
        String flagColor = teamNumber == 1 ? "<red>" : "<blue>";
        String message = flagColor + "🚩 Flag returns in " + secondsRemaining + "s";

        for (UUID playerUuid : playerUuids) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                ActionBarQueue.get().startDisplay(player, message, 1, 1100);
            }
        }
    }

}


