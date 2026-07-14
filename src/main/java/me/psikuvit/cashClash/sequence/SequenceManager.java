package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;

/**
 * Owns sequence playback for a single {@link GameSession}. Applies the input-lock flag
 * around playback when requested, and fully bypasses playback (calling onComplete
 * immediately) when sequences are disabled server-side.
 */
public class SequenceManager {

    private final GameSession session;
    private SequencePlayer activePlayer;

    public SequenceManager(GameSession session) {
        this.session = session;
    }

    public void play(Sequence sequence, boolean lockPlayers, Runnable onComplete) {
        if (!ConfigManager.getInstance().isSequencesEnabled()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        if (activePlayer != null) {
            activePlayer.cancel();
        }

        if (lockPlayers) {
            session.setSequenceLocked(true);
        }

        activePlayer = new SequencePlayer(session);
        activePlayer.play(sequence, () -> {
            if (lockPlayers) {
                session.setSequenceLocked(false);
            }
            activePlayer = null;
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Play a lightweight, non-locking sequence outside the single-slot exclusivity
     * tracking used by {@link #play}. For fire-and-forget announcements (e.g. the
     * sudden-death title) that may legitimately run alongside another in-flight,
     * locking sequence rather than cancelling it.
     */
    public void playUntracked(Sequence sequence) {
        if (!ConfigManager.getInstance().isSequencesEnabled()) {
            return;
        }
        new SequencePlayer(session).play(sequence, null);
    }

    /**
     * Cancel any in-flight sequence and clear the lock. Called when a session ends or is
     * force-progressed so a dangling scheduled task can't leak into the next game.
     */
    public void cleanup() {
        if (activePlayer != null) {
            activePlayer.cancel();
            activePlayer = null;
        }
        session.setSequenceLocked(false);
    }
}
