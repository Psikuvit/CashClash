package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.scoreboard.GameScoreboardManager;
import me.psikuvit.cashClash.scoreboard.LobbyScoreboardManager;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Facade for scoreboard management. Delegates to LobbyScoreboardManager and GameScoreboardManager.
 */
public class ScoreboardManager {

    private static ScoreboardManager instance;

    public static ScoreboardManager getInstance() {
        if (instance == null) {
            instance = new ScoreboardManager();
        }
        return instance;
    }

    /**
     * Shutdown all scoreboard systems (game scoreboards only).
     */
    public void shutdown() {
        GameScoreboardManager.getInstance().shutdown();
    }

    /**
     * Set lobby scoreboard for a player (when they join the server or leave a game).
     */
    public void setLobbyScoreboard(Player player) {
        LobbyScoreboardManager.getInstance().setScoreboard(player);
    }

    /**
     * Remove lobby scoreboard from a player.
     */
    public void removeLobbyScoreboard(Player player) {
        LobbyScoreboardManager.getInstance().removeScoreboard(player);
    }

    /**
     * Create game scoreboards for all players in a session.
     */
    public void createBoardForSession(GameSession session) {
        GameScoreboardManager.getInstance().createForSession(session);
    }

    /**
     * Add a player to an existing session's scoreboard.
     */
    public void addPlayerToSession(GameSession session, Player player) {
        GameScoreboardManager.getInstance().addPlayer(session, player);
    }

    /**
     * Remove a player from a session's scoreboard and give them lobby scoreboard.
     */
    public void removePlayerFromSession(UUID sessionId, Player player) {
        GameScoreboardManager.getInstance().removePlayer(sessionId, player);
    }

    /**
     * Remove all scoreboards for a session (when game ends).
     */
    public void removeBoard(UUID sessionId) {
        GameScoreboardManager.getInstance().removeForSession(sessionId);
    }

    /**
     * Update scoreboards for a session immediately.
     */
    public void updateSession(UUID sessionId) {
        GameScoreboardManager.getInstance().updateSession(sessionId);
    }
}
