package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Comprehensive action-bar management system with timer support.
 *
 * Features:
 * - Per-player timer tasks that auto-create and auto-stop
 * - Message updates only when countdown seconds actually change
 * - Persistent displays with priority-based queuing
 * - Custom message formatters for flexible timer displays
 */
public class ActionBarQueue {

    private static final ActionBarQueue INSTANCE = new ActionBarQueue();

    public static ActionBarQueue get() {
        return INSTANCE;
    }

    private final Map<UUID, PersistentDisplay> persistentDisplays = new HashMap<>();
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    // Timer-specific tracking
    private final Map<UUID, TimerDisplay> timerDisplays = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();
    private final Map<UUID, Long> lastDisplayedSeconds = new HashMap<>();

    private ActionBarQueue() {}

    /**
     * Start a countdown timer display for a player.
     * The timer automatically creates/manages its own task and updates the actionbar only when seconds change.
     *
     * @param player              The player to display the timer to
     * @param durationMs          Timer duration in milliseconds
     * @param priority            Display priority (lower = higher)
     * @param messageFormatter    Function taking remaining seconds (long) and returning formatted message (String)
     */
    public synchronized void startCountdownTimer(Player player, long durationMs, int priority, Function<Long, String> messageFormatter) {
        if (player == null || !player.isOnline() || durationMs <= 0 || messageFormatter == null) return;

        UUID playerUuid = player.getUniqueId();

        // Stop any existing timer for this player
        stopCountdownTimer(playerUuid);

        long expiryMs = System.currentTimeMillis() + durationMs;
        TimerDisplay timerDisplay = new TimerDisplay(expiryMs, priority, messageFormatter);
        timerDisplays.put(playerUuid, timerDisplay);
        lastDisplayedSeconds.put(playerUuid, -1L); // Initialize to -1 so first update always sends

        // Start the timer task
        startTimerTask(playerUuid);
    }

    public void startCountdownTimer(UUID playerUuid, long durationMs, int priority, Function<Long, String> messageFormatter) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            startCountdownTimer(player, durationMs, priority, messageFormatter);
        }
    }

    /**
     * Stop a countdown timer for a player
     */
    public synchronized void stopCountdownTimer(Player player) {
        if (player != null) {
            stopCountdownTimer(player.getUniqueId());
        }
    }

    public synchronized void stopCountdownTimer(UUID playerUuid) {
        if (playerUuid == null) return;

        timerDisplays.remove(playerUuid);
        lastDisplayedSeconds.remove(playerUuid);

        BukkitTask task = timerTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendActionBar(Messages.parse(""));
        }
    }

    /**
     * Internal: Start the timer task for a specific player
     */
    private void startTimerTask(UUID playerUuid) {
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            synchronized (this) {
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
    private void updateTimerDisplay(UUID playerUuid) {
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
        long secondsRemaining = remainingMs / 1000 + (remainingMs % 1000 > 0 ? 1 : 0);

        // Timer expired - cleanup
        if (remainingMs == 0) {
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
        player.sendActionBar(Messages.parse(message));
    }


    public synchronized void startDisplay(UUID playerUuid, String message, int priority, long durationMs) {
        if (playerUuid == null || message == null || durationMs <= 0) return;

        long expiryMs = System.currentTimeMillis() + durationMs;
        PersistentDisplay previous = persistentDisplays.get(playerUuid);
        if (previous != null && previous.message().equals(message) && previous.priority() == priority) {
            expiryMs = Math.max(previous.expiryMs(), expiryMs);
        } else {
            cancelRefreshTask(playerUuid);
        }

        PersistentDisplay display = new PersistentDisplay(message, priority, expiryMs);
        persistentDisplays.put(playerUuid, display);

        sendPersistentMessage(playerUuid, display);
    }

    public void startDisplay(Player player, String message, int priority, long durationMs) {
        if (player != null && player.isOnline()) {
            startDisplay(player.getUniqueId(), message, priority, durationMs);
        }
    }

    /**
     * Stop a persistent action-bar display for a player.
     */
    public synchronized void stopDisplay(UUID playerUuid) {
        if (playerUuid == null) return;
        persistentDisplays.remove(playerUuid);
        cancelRefreshTask(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendActionBar(Messages.parse(""));
        }
    }

    public void stopDisplay(Player player) {
        if (player != null) {
            stopDisplay(player.getUniqueId());
        }
    }

    /**
     * Send a persistent message directly to the player. Expires when display duration ends.
     */
    private void sendPersistentMessage(UUID playerUuid, PersistentDisplay display) {
        Player p = Bukkit.getPlayer(playerUuid);
        if (p == null || !p.isOnline()) {
            persistentDisplays.remove(playerUuid);
            cancelRefreshTask(playerUuid);
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= display.expiryMs()) {
            persistentDisplays.remove(playerUuid);
            cancelRefreshTask(playerUuid);
            return;
        }

        p.sendActionBar(Messages.parse(display.message()));

        if (refreshTasks.containsKey(playerUuid)) {
            return;
        }

        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            synchronized (this) {
                refreshTasks.remove(playerUuid);
                PersistentDisplay current = persistentDisplays.get(playerUuid);
                if (current == display) {
                    sendPersistentMessage(playerUuid, current);
                }
            }
        }, 15L);
        if (task != null) {
            refreshTasks.put(playerUuid, task);
        }
    }

    private synchronized void cancelRefreshTask(UUID playerUuid) {
        BukkitTask task = refreshTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    private record PersistentDisplay(String message, int priority, long expiryMs) {}

    private record TimerDisplay(long expiryMs, int priority, Function<Long, String> messageFormatter) {}
}

