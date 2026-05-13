package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.gamemode.impl.FlagState;
import me.psikuvit.cashClash.util.ActionBarQueue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive timer display utility for all actionbar countdown timers.
 * Manages bonus timers, heart timers, and flag return timers through ActionBarQueue.
 *
 * Each timer type:
 * - Starts when the condition is met (flag picked up, heart applied, flag dropped)
 * - Stops when the condition is no longer valid
 * - Updates only when countdown seconds actually change
 * - Is completely independent with its own task lifecycle
 */
public class TimerDisplayUtils {

    // Timer duration constants
    private static final long CAPTURE_BONUS_DURATION_MS = 45 * 1000;  // 45 seconds
    private static final long EXTRA_HEART_DURATION_MS = 45 * 1000;    // 45 seconds

    // Priorities for actionbar display (lower = higher priority)
    private static final int PRIORITY_FLAG_RETURN = 2;     // Shows when flag is dropping
    private static final int PRIORITY_BONUS_TIMER = 5;     // Shows when flag is held
    private static final int PRIORITY_HEART_TIMER = 3;     // Shows when heart is active

    private TimerDisplayUtils() {
        throw new AssertionError("Utility class");
    }

    // ========= BONUS TIMER METHODS =========

    /**
     * Start a bonus timer display for a player holding a flag.
     * The timer automatically manages itself and updates only when seconds change.
     *
     * @param player The player holding the flag
     * @param flag The flag state containing the capture time
     */
    public static void startBonusTimer(Player player, FlagState flag) {
        if (player == null || !player.isOnline() || flag == null || !flag.isHeld()) {
            return;
        }

        long captureTime = flag.captureTime();
        long now = System.currentTimeMillis();
        long elapsed = now - captureTime;
        long remaining = Math.max(0, CAPTURE_BONUS_DURATION_MS - elapsed);

        if (remaining <= 0) {
            return; // Bonus window already expired
        }

        // Start countdown timer with custom message formatter
        ActionBarQueue.get().startCountdownTimer(
            player,
            remaining,
            PRIORITY_BONUS_TIMER,
            seconds -> {
                if (seconds > 0) {
                    return "<green>⏰ Bonus expires in: <gold>" + seconds + "s</gold></green>";
                } else {
                    return "<red>❌ No bonus - score won't grant extra money</red>";
                }
            }
        );
    }

    /**
     * Stop a bonus timer for a player
     *
     * @param player The player to stop the timer for
     */
    public static void stopBonusTimer(Player player) {
        if (player != null) {
            ActionBarQueue.get().stopCountdownTimer(player);
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
        return Math.max(0, CAPTURE_BONUS_DURATION_MS - elapsed);
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

    // ========= HEART TIMER METHODS =========

    /**
     * Start a heart timer display for a player who just received an extra heart.
     * The timer automatically manages itself and updates only when seconds change.
     *
     * @param player The player who received the extra heart
     * @param playerHeartTimestamps Map to track when heart was received
     */
    public static void startHeartTimer(Player player, Map<UUID, Long> playerHeartTimestamps) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Record the heart timestamp
        long now = System.currentTimeMillis();
        playerHeartTimestamps.put(playerUuid, now);

        // Start countdown timer with custom message formatter
        ActionBarQueue.get().startCountdownTimer(
            player,
            EXTRA_HEART_DURATION_MS,
            PRIORITY_HEART_TIMER,
            seconds -> "<red>❤ Extra Heart expires in: <gold>" + seconds + "s</gold></red>"
        );
    }

    /**
     * Stop a heart timer for a player
     *
     * @param player The player to stop the timer for
     * @param playerHeartTimestamps Map to clean up
     */
    public static void stopHeartTimer(Player player, Map<UUID, Long> playerHeartTimestamps) {
        if (player != null) {
            UUID playerUuid = player.getUniqueId();
            playerHeartTimestamps.remove(playerUuid);
            ActionBarQueue.get().stopCountdownTimer(player);
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
        return Math.max(0, EXTRA_HEART_DURATION_MS - elapsed);
    }

    /**
     * Clear all heart timers
     *
     * @param playerHeartTimestamps Map to clear
     */
    public static void clearAllHeartTimers(Map<UUID, Long> playerHeartTimestamps) {
        playerHeartTimestamps.clear();
    }

    // ========= FLAG RETURN TIMER METHODS =========

    /**
     * Start a flag return timer for all players.
     * The timer automatically manages itself and updates only when seconds change.
     *
     * @param teamNumber The team number (1=Red, 2=Blue)
     * @param expiryMs The expiry time in milliseconds
     * @param playerUuids Collection of player UUIDs to display to
     */
    public static void startFlagReturnTimer(int teamNumber, long expiryMs, Collection<UUID> playerUuids) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = Math.max(0, expiryMs - now);

        if (remaining <= 0) {
            return; // Timer already expired
        }

        String flagColor = teamNumber == 1 ? "<red>" : "<blue>";

        // Start countdown timer for each player
        for (UUID playerUuid : playerUuids) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // Each player gets their own timer instance
                ActionBarQueue.get().startCountdownTimer(
                    player,
                    remaining,
                    PRIORITY_FLAG_RETURN,
                    seconds -> flagColor + "🚩 Flag returns in " + seconds + "s"
                );
            }
        }
    }

    /**
     * Stop a flag return timer for all players.
     *
     * @param playerUuids Collection of player UUIDs
     */
    public static void stopFlagReturnTimer(Collection<UUID> playerUuids) {
        if (playerUuids == null) {
            return;
        }

        for (UUID playerUuid : playerUuids) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                ActionBarQueue.get().stopCountdownTimer(player);
            }
        }
    }
}

