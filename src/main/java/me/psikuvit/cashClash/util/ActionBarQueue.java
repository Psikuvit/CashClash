package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight action-bar queue that serializes action-bar messages per-player.
 * Messages have a priority (lower is higher priority) and a duration in ticks.
 */
public class ActionBarQueue {

    private static final ActionBarQueue INSTANCE = new ActionBarQueue();

    public static ActionBarQueue get() {
        return INSTANCE;
    }

    // Per-player waiting queue (insertion order preserved). Paused messages are reinserted
    // at the front so they resume immediately after the preempting message finishes.
    private final Map<UUID, LinkedList<Entry>> queues = new HashMap<>();
    // Currently active message per player
    private final Map<UUID, Entry> active = new HashMap<>();
    // Scheduled end task per player
    private final Map<UUID, BukkitTask> timers = new HashMap<>();
    // Scheduled end timestamp (ms) per player for computing remaining time when preempted
    private final Map<UUID, Long> endTimeMs = new HashMap<>();

    public synchronized void enqueue(UUID playerUuid, String message, int priority, long durationTicks) {
        if (playerUuid == null || message == null) return;
        LinkedList<Entry> q = queues.computeIfAbsent(playerUuid, k -> new LinkedList<>());

        Entry newEntry = new Entry(message, priority, Math.max(1, durationTicks));

        Entry current = active.get(playerUuid);
        if (current == null) {
            // No active message - append and try to show
            q.addLast(newEntry);
            tryShowNext(playerUuid);
            return;
        }

        // Preempt currently showing message: cancel current task, compute remaining time,
        // put it back to the front of the queue and show the new message immediately.
        // If you prefer non-preemptive behaviour for lower-priority messages, adjust here.
        cancelTimerFor(playerUuid);

        // compute remaining ticks for current
        long remainTicks = current.remainingTicks();
        Long scheduledEnd = endTimeMs.get(playerUuid);
        if (scheduledEnd != null) {
            long remainingMs = scheduledEnd - System.currentTimeMillis();
            // if conversion produced 0 due to ms->ticks, keep at least 1 tick
            remainTicks = Math.max(1, TimeUnit.MILLISECONDS.toMillis(Math.max(0, remainingMs)) / 50);
        }

        // push paused current to front so it resumes after the new message
        q.addFirst(new Entry(current.message(), current.priority(), remainTicks));
        active.remove(playerUuid);
        endTimeMs.remove(playerUuid);

        // Now show the new message immediately
        q.addFirst(newEntry);
        tryShowNext(playerUuid);
    }

    private ActionBarQueue() {}

    public void enqueue(Player player, String message, int priority, long durationTicks) {
        if (player == null || !player.isOnline() || message == null) return;
        enqueue(player.getUniqueId(), message, priority, durationTicks);
    }

    private synchronized void tryShowNext(UUID playerUuid) {
        if (active.containsKey(playerUuid)) return; // already showing

        LinkedList<Entry> q = queues.get(playerUuid);
        if (q == null || q.isEmpty()) return;

        // Choose next message: prefer lowest priority value; among equals, pick earliest
        int bestIdx = 0;
        int bestPriority = q.getFirst().priority();
        for (int i = 1; i < q.size(); i++) {
            int p = q.get(i).priority();
            if (p < bestPriority) {
                bestPriority = p;
                bestIdx = i;
            }
        }

        Entry next = q.remove(bestIdx);
        if (next == null) return;

        Player p = Bukkit.getPlayer(playerUuid);
        if (p == null || !p.isOnline()) {
            // drop queued messages for offline player
            queues.remove(playerUuid);
            return;
        }

        // show immediately
        active.put(playerUuid, next);
        p.sendActionBar(Messages.parse(next.message()));

        long ticks = Math.max(1, next.remainingTicks());
        long endMs = System.currentTimeMillis() + ticks * 50L;
        endTimeMs.put(playerUuid, endMs);

        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            // End of this message display - remove active and show next
            synchronized (ActionBarQueue.this) {
                active.remove(playerUuid);
                endTimeMs.remove(playerUuid);
                timers.remove(playerUuid);
                tryShowNext(playerUuid);
            }
        }, ticks);

        if (task != null) timers.put(playerUuid, task);
    }

    public synchronized void clear(UUID playerUuid) {
        queues.remove(playerUuid);
        Entry a = active.remove(playerUuid);
        endTimeMs.remove(playerUuid);
        BukkitTask t = timers.remove(playerUuid);
        if (t != null) t.cancel();
    }

    private synchronized void cancelTimerFor(UUID playerUuid) {
        BukkitTask t = timers.remove(playerUuid);
        if (t != null) t.cancel();
    }

    private record Entry(String message, int priority, long remainingTicks) {}
}

