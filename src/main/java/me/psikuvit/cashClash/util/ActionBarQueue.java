package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the actual sending/clearing of actionbar text, plus a simple priority-based "persistent
 * display" system (show this message, refreshed until it expires). Countdown-timer behavior
 * (per-second formatting, completion messages, tick tasks) lives in {@code TimerDisplayUtils}
 * instead - that's the only place callers should reach for a countdown, going through this
 * class's {@link #sendRaw} to actually put text on screen. Keeping the two concerns in separate
 * classes means a caller can no longer accidentally stop the wrong kind of display, the way a
 * mixed {@code startCountdownTimer}/{@code stopDisplay} pairing used to when both lived here.
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
     * Send raw MiniMessage text to a player's actionbar immediately, bypassing the persistent
     * display/priority system entirely. The single funnel every other actionbar-writing class
     * (e.g. {@code TimerDisplayUtils}) should send through, so this class stays the one place
     * that actually calls {@code Player#sendActionBar}.
     */
    public void sendRaw(UUID playerUuid, String miniMessage) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendActionBar(Messages.parse(miniMessage == null ? "" : miniMessage));
        }
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
        boolean hadDisplay = persistentDisplays.remove(playerUuid) != null;
        boolean hadTask = refreshTasks.containsKey(playerUuid);
        cancelRefreshTask(playerUuid);
        if (hadDisplay || hadTask) {
            sendRaw(playerUuid, "");
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

        sendRaw(playerUuid, display.message());

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
