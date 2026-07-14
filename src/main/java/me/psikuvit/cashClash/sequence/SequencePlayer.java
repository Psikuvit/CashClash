package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes a single {@link Sequence} against a session by scheduling each step with
 * cumulative tick delays, then invokes a completion callback once every step has run.
 */
public class SequencePlayer {

    private final GameSession session;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private boolean cancelled;

    public SequencePlayer(GameSession session) {
        this.session = session;
    }

    public void play(Sequence sequence, Runnable onComplete) {
        long cumulativeDelay = 0L;

        for (Sequence.Entry entry : sequence.getEntries()) {
            cumulativeDelay += entry.delayTicks();
            long delay = cumulativeDelay;
            scheduledTasks.add(SchedulerUtils.runTaskLater(() -> {
                if (cancelled) return;
                entry.step().accept(session);
            }, delay));
        }

        scheduledTasks.add(SchedulerUtils.runTaskLater(() -> {
            if (!cancelled && onComplete != null) onComplete.run();
        }, cumulativeDelay));
    }

    /**
     * Cancel all remaining scheduled steps and skip the completion callback.
     * Used when a session ends or is force-progressed mid-sequence.
     */
    public void cancel() {
        cancelled = true;
        for (BukkitTask task : scheduledTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        scheduledTasks.clear();
    }
}
