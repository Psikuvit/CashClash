package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.game.GameSession;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all active game sessions
 */
public class GameManager {

    private static GameManager instance;
    private final Map<UUID, GameSession> activeSessions;
    private final Map<UUID, GameSession> playerToSession;
    private final Map<Integer, GameSession> arenaToSession;

    private GameManager() {
        this.activeSessions = new HashMap<>();
        this.playerToSession = new HashMap<>();
        this.arenaToSession = new HashMap<>();
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
        }
    }

    public GameSession getPlayerSession(Player player) {
        return playerToSession.get(player.getUniqueId());
    }

    public void addPlayerToSession(Player player, GameSession session) {
        playerToSession.put(player.getUniqueId(), session);
    }

    public void removePlayerFromSession(Player player) {
        playerToSession.remove(player.getUniqueId());
    }

    public Collection<GameSession> getActiveSessions() {
        return activeSessions.values();
    }

    public void shutdown() {
        activeSessions.values().forEach(GameSession::end);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        activeSessions.clear();
        playerToSession.clear();
    }
}
