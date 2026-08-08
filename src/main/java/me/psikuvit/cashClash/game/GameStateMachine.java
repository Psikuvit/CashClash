package me.psikuvit.cashClash.game;

import me.psikuvit.cashClash.util.Messages;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates and performs {@link GameState} transitions for a single {@link GameSession}. Replaces
 * raw {@code state = GameState.X} field assignments scattered through GameSession with a single
 * choke point that rejects transitions that don't make sense for a match's lifecycle (e.g. jumping
 * straight from WAITING to COMBAT, or transitioning out of ENDING).
 */
public class GameStateMachine {

    private static final Map<GameState, Set<GameState>> ALLOWED_TRANSITIONS = new EnumMap<>(GameState.class);

    static {
        ALLOWED_TRANSITIONS.put(GameState.WAITING, EnumSet.of(GameState.SHOPPING, GameState.ENDING));
        ALLOWED_TRANSITIONS.put(GameState.BUFF_SELECTION, EnumSet.of(GameState.SHOPPING, GameState.ENDING));
        ALLOWED_TRANSITIONS.put(GameState.SHOPPING, EnumSet.of(GameState.COMBAT, GameState.BUFF_SELECTION, GameState.ENDING));
        ALLOWED_TRANSITIONS.put(GameState.COMBAT, EnumSet.of(GameState.SHOPPING, GameState.BUFF_SELECTION, GameState.ENDING));
        ALLOWED_TRANSITIONS.put(GameState.ENDING, EnumSet.noneOf(GameState.class));
    }

    private GameState current;

    public GameStateMachine(GameState initial) {
        this.current = initial;
    }

    public GameState getCurrent() {
        return current;
    }

    /**
     * @return true if {@code next} is either the current state (a no-op transition) or reachable
     *         from it per {@link #ALLOWED_TRANSITIONS}.
     */
    public boolean canTransitionTo(GameState next) {
        return next == current || ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    /**
     * Transitions to {@code next}. An invalid transition is logged and ignored rather than
     * thrown - a match already in progress should keep running even if a caller's transition
     * turns out to be stale/out of order, matching this codebase's general "don't let one bad
     * step break the whole session" posture.
     */
    public void transitionTo(GameState next) {
        if (!canTransitionTo(next)) {
            Messages.debug("GAME", "Rejected invalid GameState transition: " + current + " -> " + next);
            return;
        }
        current = next;
    }
}
