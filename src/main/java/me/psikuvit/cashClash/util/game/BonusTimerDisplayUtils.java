package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.gamemode.impl.FlagState;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing capture bonus timer displays
 */
public class BonusTimerDisplayUtils {

    private static final long CAPTURE_TIMER_MS = 45 * 1000; // 45 seconds for bonus

    private BonusTimerDisplayUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Start a task that displays bonus timer to all players
     *
     * @param flagStates Map of flag states
     * @param playerUuids Collection of player UUIDs
     * @return The BukkitTask managing the display
     */
    public static BukkitTask startBonusTimerDisplayTask(Map<Integer, FlagState> flagStates, Collection<UUID> playerUuids) {
        return SchedulerUtils.runTaskTimer(() -> updateBonusTimerDisplay(flagStates, playerUuids), 0, 10);
    }

    /**
     * Update bonus timer display for all players
     * Shows countdown to flag carriers and observes bonus window expiration for all
     *
     * @param flagStates Map of current flag states
     * @param playerUuids Collection of all player UUIDs
     */
    public static void updateBonusTimerDisplay(Map<Integer, FlagState> flagStates, Collection<UUID> playerUuids) {
        long now = System.currentTimeMillis();

        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        displayBonusTimerForFlag(redFlag, "red", now, playerUuids);
        displayBonusTimerForFlag(blueFlag, "blue", now, playerUuids);
    }

    /**
     * Display bonus timer for a specific flag to all players
     *
     * @param flag The flag state
     * @param colorTag The color tag for logging
     * @param now Current time in milliseconds
     * @param playerUuids All player UUIDs
     */
    private static void displayBonusTimerForFlag(FlagState flag, String colorTag, long now, Iterable<UUID> playerUuids) {
        if (flag == null || !flag.isHeld()) {
            return;
        }

        long elapsed = now - flag.captureTime();
        long remaining = Math.max(0, CAPTURE_TIMER_MS - elapsed);
        long secondsRemaining = remaining / 1000 + (remaining % 1000 > 0 ? 1 : 0);

        String timerMsg;
        if (remaining > 0) {
            timerMsg = "<green>⏰ Bonus expires in: <gold>" + secondsRemaining + "s</gold></green>";
        } else {
            timerMsg = "<red>❌ No bonus - score won't grant extra money</red>";
        }

        for (UUID playerUuid : playerUuids) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                long displayDuration = remaining > 0 ? remaining : 5000;
                ActionBarQueue.get().startDisplay(player, timerMsg, 5, displayDuration);
            }
        }
    }

    /**
     * Get bonus time remaining for a flag in milliseconds
     *
     * @param flag The flag state
     * @return Remaining bonus time, or 0 if expired
     */
    public static long getBonusTimeRemaining(FlagState flag) {
        if (flag == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - flag.captureTime();
        return Math.max(0, CAPTURE_TIMER_MS - elapsed);
    }

    /**
     * Check if flag is within bonus window (45 seconds of capture)
     *
     * @param flag The flag state
     * @return True if flag is held and within bonus window
     */
    public static boolean isWithinBonusWindow(FlagState flag) {
        return flag != null && flag.isHeld() && getBonusTimeRemaining(flag) > 0;
    }
}




