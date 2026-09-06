package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;

import java.util.function.Consumer;

/**
 * Owns sequence playback for a single {@link GameSession}. Applies the relevant
 * session-level restriction flag around playback when requested, and fully bypasses
 * playback (calling onComplete immediately) when sequences are disabled server-side.
 */
public class SequenceManager {

    private final GameSession session;
    private SequencePlayer activePlayer;
    private Runnable activeRelease;

    public SequenceManager(GameSession session) {
        this.session = session;
    }

    /**
     * Play a sequence, optionally freezing player movement (via {@link GameSession#setSequenceLocked})
     * for its duration. Used by the blind+freeze reveal moments (round start, president
     * reveal, shield reveal).
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
        if (!CashClashPlugin.getInstance().getConfigManager().isSequencesEnabled()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        cancelActive();

        if (flagSetter != null) {
            flagSetter.accept(true);
            activeRelease = () -> flagSetter.accept(false);
        }

        Messages.debug("SEQUENCE", "Starting sequence playback for session " + session.getSessionId()
                + " (" + sequence.getEntries().size() + " step(s))");

        activePlayer = new SequencePlayer(session);
        activePlayer.play(sequence, () -> {
            if (flagSetter != null) {
                flagSetter.accept(false);
            }
            activePlayer = null;
            activeRelease = null;
            Messages.debug("SEQUENCE", "Sequence completed normally for session " + session.getSessionId());
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Cancel the current sequence, if any, and immediately release whatever restriction flag it
     * had turned on - {@link SequencePlayer#cancel} skips its own completion step, so without
     * this a pre-empted sequence's flag would stay stuck on for the rest of the game.
     */
    private void cancelActive() {
        if (activePlayer != null) {
            activePlayer.cancel();
            activePlayer = null;
            Messages.debug("SEQUENCE", "Pre-empted an in-flight sequence for session " + session.getSessionId());
        }
        if (activeRelease != null) {
            activeRelease.run();
            activeRelease = null;
            Messages.debug("SEQUENCE", "Released the pre-empted sequence's restriction flag for session " + session.getSessionId());
        }
    }

    /**
     * Play a lightweight, non-locking sequence outside the single-slot exclusivity
     * tracking used by {@link #play}. For fire-and-forget announcements (e.g. the
     * sudden-death title) that may legitimately run alongside another in-flight,
     * locking sequence rather than cancelling it.
     */
    public void playUntracked(Sequence sequence) {
        if (!CashClashPlugin.getInstance().getConfigManager().isSequencesEnabled()) {
            return;
        }
        new SequencePlayer(session).play(sequence, null);
    }

    /**
     * Cancel any in-flight sequence and clear the lock. Called when a session ends or is
     * force-progressed so a dangling scheduled task can't leak into the next game.
     */
    public void cleanup() {
        cancelActive();
        session.resetSequenceLocked();
        session.setActionsRestricted(false);
        session.setDamageDisabled(false);
    }
}
