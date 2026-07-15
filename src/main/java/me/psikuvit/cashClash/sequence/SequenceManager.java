package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;

import java.util.function.Consumer;

/**
 * Owns sequence playback for a single {@link GameSession}. Applies the relevant
 * session-level restriction flag around playback when requested, and fully bypasses
 * playback (calling onComplete immediately) when sequences are disabled server-side.
 */
public class SequenceManager {

    private final GameSession session;
    private SequencePlayer activePlayer;

    public SequenceManager(GameSession session) {
        this.session = session;
    }

    /**
     * Play a sequence, optionally freezing player movement (via {@link GameSession#setSequenceLocked})
     * for its duration. Used by the blind+freeze reveal moments (round start, president
     * reveal, round 4 transition).
     */
    public void play(Sequence sequence, boolean lockPlayers, Runnable onComplete) {
        playInternal(sequence, lockPlayers ? session::setSequenceLocked : null, onComplete);
    }

    /**
     * Play a sequence with shopping-phase-parity restrictions (no custom item use, no
     * block placement, no damage, no scoreboard updates) applied for its duration, without
     * freezing movement. Used by the round-end sequence.
     */
    public void playRestricted(Sequence sequence, Runnable onComplete) {
        playInternal(sequence, session::setActionsRestricted, onComplete);
    }

    /**
     * Play a sequence with only damage/PvP disabled for its duration, no other
     * restrictions. Used by the game-victory sequence.
     */
    public void playDamageDisabled(Sequence sequence, Runnable onComplete) {
        playInternal(sequence, session::setDamageDisabled, onComplete);
    }

    private void playInternal(Sequence sequence, Consumer<Boolean> flagSetter, Runnable onComplete) {
        if (!ConfigManager.getInstance().isSequencesEnabled()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        if (activePlayer != null) {
            activePlayer.cancel();
        }

        if (flagSetter != null) {
            flagSetter.accept(true);
        }

        activePlayer = new SequencePlayer(session);
        activePlayer.play(sequence, () -> {
            if (flagSetter != null) {
                flagSetter.accept(false);
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
        session.setActionsRestricted(false);
        session.setDamageDisabled(false);
    }
}
