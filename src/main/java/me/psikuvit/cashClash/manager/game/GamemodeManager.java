package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gamemode.impl.KillConfirmGamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.util.Messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Manages gamemode selection and tracking for game sessions
 */
public class GamemodeManager {

    private static GamemodeManager instance;
    private final Map<UUID, Gamemode> sessionGamemodes = new HashMap<>();
    private final Map<UUID, GamemodeType> nextGamemode = new HashMap<>();
    private final Random random = new Random();

    public static synchronized GamemodeManager getInstance() {
        if (instance == null) {
            instance = new GamemodeManager();
        }
        return instance;
    }

    /**
     * Set the next gamemode for a session
     */
    public void setNextGamemode(UUID sessionId, GamemodeType type) {
        nextGamemode.put(sessionId, type);
    }

    /**
     * Create and select a random gamemode for a session
     */
    public Gamemode selectGamemode(GameSession session) {
        Gamemode gamemode;
        if (nextGamemode.containsKey(session.getSessionId())) {
            GamemodeType type = nextGamemode.remove(session.getSessionId());
            gamemode = createGamemode(session, type);
        } else {
            gamemode = createRandomGamemode(session);
        }
        sessionGamemodes.put(session.getSessionId(), gamemode);
        return gamemode;
    }

    /**
     * Create a specific gamemode
     */
    private Gamemode createGamemode(GameSession session, GamemodeType type) {
        Messages.debug("GAMEMODE", "Selected " + type.getDisplayName() + " for session " + session.getSessionId());

        return switch (type) {
            case PROTECT_THE_PRESIDENT -> new ProtectThePresidentGamemode(session);
            case CAPTURE_THE_FLAG -> new CaptureTheFlagGamemode(session);
            case KILL_CONFIRM -> new KillConfirmGamemode(session);
        };
    }

    /**
     * Get the gamemode for a session
     */
    public Gamemode getGamemode(UUID sessionId) {
        return sessionGamemodes.get(sessionId);
    }

    /**
     * Get the gamemode for a session by GameSession object
     */
    public Gamemode getGamemode(GameSession session) {
        return getGamemode(session.getSessionId());
    }

    /**
     * Create a random gamemode
     */
    private Gamemode createRandomGamemode(GameSession session) {
        GamemodeType[] types = GamemodeType.values();
        GamemodeType selected = types[random.nextInt(types.length)];

        return createGamemode(session, selected);
    }

    /**
     * Remove gamemode when game ends
     */
    public void removeGamemode(UUID sessionId) {
        Gamemode gamemode = sessionGamemodes.remove(sessionId);
        if (gamemode != null) {
            gamemode.cleanup();
        }
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        for (Gamemode gamemode : sessionGamemodes.values()) {
            gamemode.cleanup();
        }
        sessionGamemodes.clear();
    }
}

