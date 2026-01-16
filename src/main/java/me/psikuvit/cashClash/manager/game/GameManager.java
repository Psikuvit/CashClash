package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active game sessions
 */
public class GameManager {

    private static GameManager instance;
    private final Map<UUID, GameSession> activeSessions;
    private final Map<UUID, GameSession> playerToSession;
    private final Map<Integer, GameSession> arenaToSession;

    private GameManager() {
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerToSession = new ConcurrentHashMap<>();
        this.arenaToSession = new ConcurrentHashMap<>();
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public GameSession createSession(int arenaNumber) {
        GameSession session = new GameSession(arenaNumber);
        activeSessions.put(session.getSessionId(), session);
        arenaToSession.put(arenaNumber, session);
        Messages.debug("GAME", "Registered new session " + session.getSessionId() + " for arena " + arenaNumber);
        return session;
    }

    public GameSession getSessionForArena(int arenaNumber) {
        return arenaToSession.get(arenaNumber);
    }

    public void removeSession(UUID sessionId) {
        GameSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.getPlayers().forEach(playerToSession::remove);
            playerToSession.entrySet().removeIf(e ->
                    e.getValue() != null && e.getValue().getSessionId().equals(sessionId));
            arenaToSession.values().removeIf(s -> s.getSessionId().equals(sessionId));
            Messages.debug("GAME", "Removed session " + sessionId);
        }
    }

    public GameSession getPlayerSession(Player player) {
        return playerToSession.get(player.getUniqueId());
    }

    public void addPlayerToSession(Player player, GameSession session) {
        playerToSession.put(player.getUniqueId(), session);
        Messages.debug(player, "GAME", "Added to session " + session.getSessionId());
    }

    public void removePlayerFromSession(Player player) {
        playerToSession.remove(player.getUniqueId());
        Messages.debug(player, "GAME", "Removed from session");
    }

    public Collection<GameSession> getActiveSessions() {
        return activeSessions.values();
    }

    /**
     * Shutdown all game sessions and clear all data.
     * Called on plugin disable.
     */
    public void shutdown() {
        // End all active sessions
        for (GameSession session : activeSessions.values()) {
            try {
                session.end();
            } catch (Exception e) {
                Messages.debug(Messages.DebugCategory.GAME, "Error ending session " + session.getSessionId() + ": " + e.getMessage());
            }
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        activeSessions.clear();
        playerToSession.clear();
        arenaToSession.clear();

        Messages.debug(Messages.DebugCategory.GAME, "GameManager shutdown complete");
    }
}
