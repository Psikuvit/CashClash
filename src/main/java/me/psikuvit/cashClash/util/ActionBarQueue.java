package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
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

    private record Entry(String message, int priority, long durationTicks) {}

    private final Map<UUID, PriorityQueue<Entry>> queues = new HashMap<>();
    private final Map<UUID, Boolean> showing = new HashMap<>();
    private final Map<UUID, BukkitTask> timers = new HashMap<>();

    private ActionBarQueue() {}

    public void enqueue(Player player, String message, int priority, long durationTicks) {
        if (player == null || !player.isOnline() || message == null) return;
        enqueue(player.getUniqueId(), message, priority, durationTicks);
    }

    public synchronized void enqueue(UUID playerUuid, String message, int priority, long durationTicks) {
        if (playerUuid == null || message == null) return;
        PriorityQueue<Entry> q = queues.computeIfAbsent(playerUuid,
                k -> new PriorityQueue<>(Comparator.comparingInt(Entry::priority))
        );

        q.add(new Entry(message, priority, durationTicks));
        tryShowNext(playerUuid);
    }

    private synchronized void tryShowNext(UUID playerUuid) {
        Boolean isShowing = showing.get(playerUuid);
        if (isShowing != null && isShowing) return;

        PriorityQueue<Entry> q = queues.get(playerUuid);
        if (q == null || q.isEmpty()) return;

        Entry next = q.poll();
        if (next == null) return;

        Player p = Bukkit.getPlayer(playerUuid);
        if (p == null || !p.isOnline()) {
            // drop queued messages for offline player
            queues.remove(playerUuid);
            return;
        }

        showing.put(playerUuid, true);
        p.sendActionBar(Messages.parse(next.message()));

        // schedule end of display
        Bukkit.getScheduler().runTaskLaterAsynchronously(me.psikuvit.cashClash.CashClashPlugin.getInstance(), () -> {
            // run on main thread to send next message
            Bukkit.getScheduler().runTask(me.psikuvit.cashClash.CashClashPlugin.getInstance(), () -> {
                showing.put(playerUuid, false);
                timers.remove(playerUuid);
                tryShowNext(playerUuid);
            });
        }, Math.max(1, next.durationTicks()));
    }

    public synchronized void clear(UUID playerUuid) {
        queues.remove(playerUuid);
        Boolean sh = showing.remove(playerUuid);
        BukkitTask t = timers.remove(playerUuid);
        if (t != null) t.cancel();
    }
}

