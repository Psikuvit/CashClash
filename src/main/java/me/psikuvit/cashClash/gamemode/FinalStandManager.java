package me.psikuvit.cashClash.gamemode;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manager responsible for handling Final Stand independent of Sudden Death.
 *
 * Responsibilities:
 * - Start/cancel a final-stand timer
 * - Track start time and remaining time for placeholders
 * - Fire Gamemode.onFinalStandActivated() when timer elapses
 */
public class FinalStandManager {

    private final GameSession session;
    private final Gamemode gamemode;
    private final long durationMs;

    private boolean active;
    private long startMs;
    private BukkitTask task;
    private boolean unlimited;

    public FinalStandManager(GameSession session, Gamemode gamemode, long durationMs) {
        this.session = session;
        this.gamemode = gamemode;
        this.durationMs = durationMs;
        this.active = false;
        this.startMs = 0;
        this.task = null;
        this.unlimited = false;
    }

    public FinalStandManager(GameSession session, Gamemode gamemode) {
        this(session, gamemode, 3 * 60 * 1000L); // default 3 minutes
    }

    /**
     * Start the final-stand timer. If already active this is a no-op.
     */
    public void start() {
        if (active) {
            Messages.debug("[FinalStandManager] Final stand already active");
            return;
        }
        active = true;
        startMs = System.currentTimeMillis();
        unlimited = false;

        long ticksDelay = Math.max(1, durationMs / 50);
        task = SchedulerUtils.runTaskLater(this::activate, ticksDelay);
        Messages.debug("[FinalStandManager] Final stand timer started for " + durationMs + "ms");
    }

    /**
     * Start final-stand immediately and keep it active until explicitly cancelled.
     * This mode is used when final-stand should be unlimited time until a winner is found.
     * It will immediately notify the gamemode via onFinalStandActivated().
     */
    public void startUntilWin() {
        if (active) {
            Messages.debug("[FinalStandManager] Final stand already active");
            return;
        }
        active = true;
        startMs = System.currentTimeMillis();
        unlimited = true;
        // Do not schedule a timer; final-stand remains until cancelled
        Messages.debug("[FinalStandManager] Final stand started (until-win mode)");
        if (gamemode != null) gamemode.onFinalStandActivated();
    }

    private void activate() {
        Messages.debug("[FinalStandManager] Final stand timer elapsed - activating final stand");
        if (gamemode != null) {
            gamemode.onFinalStandActivated();
        }
    }

    /**
     * Cancel the active final-stand timer and mark it inactive.
     */
    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
        active = false;
        startMs = 0;
        unlimited = false;
        Messages.debug("[FinalStandManager] Final stand cancelled");
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Remaining seconds of the final-stand timer, or -1 if not active
     */
    public int getRemainingSeconds() {
        long ms = getRemainingMs();
        return ms < 0 ? -1 : (int) (ms / 1000);
    }

    /**
     * Remaining milliseconds of the final-stand timer, or -1 if not active
     */
    public long getRemainingMs() {
        if (!active || startMs <= 0) return -1;
        if (unlimited) return -1;
        long elapsed = System.currentTimeMillis() - startMs;
        long remaining = durationMs - elapsed;
        return Math.max(remaining, 0);
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        cancel();
    }

}

