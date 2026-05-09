package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight action-bar queue that serializes action-bar messages per-player.
 * Messages have a priority (lower is higher priority) and a duration in ticks.
 */
public class ActionBarQueue {

    private static final ActionBarQueue INSTANCE = new ActionBarQueue();

    public static ActionBarQueue get() {
        return INSTANCE;
    }

    private final Map<UUID, PersistentDisplay> persistentDisplays = new HashMap<>();
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    private ActionBarQueue() {}

    /**
     * Start a persistent action-bar display that auto-renews until stopped or expired.
     * If a display is already active for this player, it is updated with the new message/duration.
     *
     * @param playerUuid   player UUID
     * @param message      action-bar message (will be auto-refreshed)
     * @param priority     display priority (lower = higher)
     * @param durationMs   how long to display (e.g., 45000 for 45 seconds)
     */
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
}

