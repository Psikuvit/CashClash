package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.gamemode.impl.FlagState;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Owns every actionbar countdown timer in the plugin: the generic engine (state tracking, the
 * per-tick task, second-change-only updates, optional completion message) plus the named timer
 * types built on it (bonus, heart, flag return, and any gamemode-specific countdown that calls
 * {@link #startCountdownTimer}/{@link #stopCountdownTimer} directly). Rendering itself is always
 * routed through {@link ActionBarQueue#sendRaw} - this class never touches
 * {@code Player#sendActionBar} on its own, so there's exactly one place that actually writes to
 * a player's actionbar.
 *
 * All-static with static mutable state, same pattern as {@code RuneManager} - a single shared
 * engine rather than a per-caller instance, since a player can only ever be shown one timer at a
 * time regardless of which system started it (see the priority handling in
 * {@link #startCountdownTimer(Player, long, int, Function, String)}).
 */
public class TimerDisplayUtils {

    /**
     * CTF's capture-bonus window - kept in sync with {@code CaptureTheFlagGamemode}'s own
     * CAPTURE_TIMER_MS (same config key) rather than a locally duplicated constant, since a
     * duplicate here would silently drift from the gamemode's actual bonus-window check.
     */
    private static long captureBonusDurationMs() {
        return CashClashPlugin.getInstance().getConfigManager().getCTFCaptureBonusTimerMs();
    }

    private static long extraHeartDurationMs() {
        return CashClashPlugin.getInstance().getConfigManager().getCTFHeartBonusDurationMs();
    }

    // Priorities for actionbar display (lower = higher priority)
    private static final int PRIORITY_FLAG_RETURN = 2;     // Shows when flag is dropping
    private static final int PRIORITY_BONUS_TIMER = 5;     // Shows when flag is held
    private static final int PRIORITY_HEART_TIMER = 3;     // Shows when heart is active

    // ==================== TIMER ENGINE (moved from ActionBarQueue) ====================

    private static final Map<UUID, TimerDisplay> timerDisplays = new HashMap<>();
    private static final Map<UUID, BukkitTask> timerTasks = new HashMap<>();
    private static final Map<UUID, Long> lastDisplayedSeconds = new HashMap<>();
    private static final Map<UUID, String> timerCompletionMessages = new HashMap<>();

    private record TimerDisplay(long expiryMs, int priority, Function<Long, String> messageFormatter) {}

    private TimerDisplayUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Start a countdown timer display for a player.
     * The timer automatically creates/manages its own task and updates the actionbar only when seconds change.
     *
     * @param player              The player to display the timer to
     * @param durationMs          Timer duration in milliseconds
     * @param priority            Display priority (lower = higher)
     * @param messageFormatter    Function taking remaining seconds (long) and returning formatted message (String)
     * @param completionMessage   Optional message to display when timer completes (null for no completion message)
     */
    public static synchronized void startCountdownTimer(Player player, long durationMs, int priority, Function<Long, String> messageFormatter, String completionMessage) {
        if (player == null || !player.isOnline() || durationMs <= 0 || messageFormatter == null) return;

        UUID playerUuid = player.getUniqueId();

        // Lower priority number wins: a higher-precedence display already showing (e.g. a flag
        // return countdown) must not be stomped by a lower-precedence one (e.g. a bonus timer)
        // starting or refreshing elsewhere.
        TimerDisplay currentDisplay = timerDisplays.get(playerUuid);
        if (currentDisplay != null && currentDisplay.priority() < priority) {
            return;
        }

        long expiryMs = System.currentTimeMillis() + durationMs;
        TimerDisplay existingDisplay = timerDisplays.get(playerUuid);
        Long existingLastSeconds = lastDisplayedSeconds.get(playerUuid);
        long remainingSeconds = calculateSecondsRemaining(durationMs);

        if (existingDisplay == null || existingDisplay.priority() != priority) {
            stopCountdownTimer(playerUuid);
            existingLastSeconds = -1L;
        }

        TimerDisplay timerDisplay = new TimerDisplay(expiryMs, priority, messageFormatter);
        timerDisplays.put(playerUuid, timerDisplay);
        lastDisplayedSeconds.put(playerUuid, existingLastSeconds == null ? -1L : existingLastSeconds);
        if (completionMessage != null) {
            timerCompletionMessages.put(playerUuid, completionMessage);
        } else {
            timerCompletionMessages.remove(playerUuid);
        }

        if (!timerTasks.containsKey(playerUuid)) {
            startTimerTask(playerUuid);
        } else if (existingLastSeconds == null || existingLastSeconds != remainingSeconds) {
            updateTimerDisplay(playerUuid);
        }
    }

    /**
     * Start a countdown timer display for a player.
     * The timer automatically creates/manages its own task and updates the actionbar only when seconds change.
     *
     * @param player              The player to display the timer to
     * @param durationMs          Timer duration in milliseconds
     * @param priority            Display priority (lower = higher)
     * @param messageFormatter    Function taking remaining seconds (long) and returning formatted message (String)
     */
    public static synchronized void startCountdownTimer(Player player, long durationMs, int priority, Function<Long, String> messageFormatter) {
        startCountdownTimer(player, durationMs, priority, messageFormatter, null);
    }

    public static void startCountdownTimer(UUID playerUuid, long durationMs, int priority, Function<Long, String> messageFormatter) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            startCountdownTimer(player, durationMs, priority, messageFormatter);
        }
    }

    public static void startCountdownTimer(UUID playerUuid, long durationMs, int priority, Function<Long, String> messageFormatter, String completionMessage) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            startCountdownTimer(player, durationMs, priority, messageFormatter, completionMessage);
        }
    }

    /**
     * Stop a countdown timer for a player
     */
    public static synchronized void stopCountdownTimer(Player player) {
        if (player != null) {
            stopCountdownTimer(player.getUniqueId());
        }
    }

    public static synchronized void stopCountdownTimer(UUID playerUuid) {
        if (playerUuid == null) return;

        boolean hadTimer = timerDisplays.remove(playerUuid) != null;
        lastDisplayedSeconds.remove(playerUuid);
        timerCompletionMessages.remove(playerUuid);

        BukkitTask task = timerTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }

        if (hadTimer) {
            ActionBarQueue.get().sendRaw(playerUuid, "");
        }
    }

    /**
     * Stop a player's countdown timer only if it's currently showing the given priority - so a
     * broadcast stop (e.g. every session player, when a flag return pauses) can't wipe an
     * unrelated timer (e.g. that player's own bonus timer) it never started.
     */
    private static synchronized void stopCountdownTimerIfPriority(UUID playerUuid, int priority) {
        if (playerUuid == null) return;
        TimerDisplay display = timerDisplays.get(playerUuid);
        if (display != null && display.priority() == priority) {
            stopCountdownTimer(playerUuid);
        }
    }

    /**
     * Internal: Start the timer task for a specific player
     */
    private static void startTimerTask(UUID playerUuid) {
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            synchronized (TimerDisplayUtils.class) {
                updateTimerDisplay(playerUuid);
            }
        }, 0, 2); // Check every 2 ticks (100ms) for smooth transitions

        if (task != null) {
            timerTasks.put(playerUuid, task);
        }
    }

    /**
     * Internal: Update timer display for a player - only sends message if seconds have changed
     */
    private static void updateTimerDisplay(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            stopCountdownTimer(playerUuid);
            return;
        }

        TimerDisplay timerDisplay = timerDisplays.get(playerUuid);
        if (timerDisplay == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long remainingMs = Math.max(0, timerDisplay.expiryMs() - now);
        long secondsRemaining = calculateSecondsRemaining(remainingMs);

        // Timer expired - show completion message if provided
        if (remainingMs == 0) {
            String completionMessage = timerCompletionMessages.get(playerUuid);
            if (completionMessage != null) {
                ActionBarQueue.get().sendRaw(playerUuid, completionMessage);
            }
            stopCountdownTimer(playerUuid);
            return;
        }

        // Only update if seconds have changed
        Long lastSeconds = lastDisplayedSeconds.get(playerUuid);
        if (lastSeconds != null && lastSeconds == secondsRemaining) {
            return;
        }

        lastDisplayedSeconds.put(playerUuid, secondsRemaining);

        // Generate message using formatter and send
        String message = timerDisplay.messageFormatter().apply(secondsRemaining);
        ActionBarQueue.get().sendRaw(playerUuid, message);
    }

    private static long calculateSecondsRemaining(long remainingMs) {
        return remainingMs / 1000 + (remainingMs % 1000 > 0 ? 1 : 0);
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
        long remaining = Math.max(0, captureBonusDurationMs() - elapsed);

        if (remaining <= 0) {
            return; // Bonus window already expired
        }

        // Start countdown timer with custom message formatter and completion message
        startCountdownTimer(
            player,
            remaining,
            PRIORITY_BONUS_TIMER,
            seconds -> {
                if (seconds > 0) {
                    return "<green>⏰ Bonus expires in: <gold>" + seconds + "s</gold></green>";
                } else {
                    return "<red>❌ No bonus - score won't grant extra money</red>";
                }
            },
            "<green>✓ You will receive money bonus!</green>"
        );
    }

    /**
     * Stop a bonus timer for a player
     *
     * @param player The player to stop the timer for
     */
    public static void stopBonusTimer(Player player) {
        if (player != null) {
            stopCountdownTimerIfPriority(player.getUniqueId(), PRIORITY_BONUS_TIMER);
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
        return Math.max(0, captureBonusDurationMs() - elapsed);
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
        startCountdownTimer(
            player,
            extraHeartDurationMs(),
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
            stopCountdownTimerIfPriority(playerUuid, PRIORITY_HEART_TIMER);
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
        return Math.max(0, extraHeartDurationMs() - elapsed);
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
                startCountdownTimer(
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
            stopCountdownTimerIfPriority(playerUuid, PRIORITY_FLAG_RETURN);
        }
    }
}
