package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.SessionBoard;
import me.psikuvit.cashClash.game.GameSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages per-game session scoreboards. Creates, updates and removes scoreboards when sessions start/end.
 */
public class ScoreboardManager {

    private static ScoreboardManager instance;

    private final Map<UUID, SessionBoard> boards;

    private ScoreboardManager() {
        this.boards = new HashMap<>();
    }

    public static ScoreboardManager getInstance() {
        if (instance == null) instance = new ScoreboardManager();
        return instance;
    }

    /**
     * Create and attach a scoreboard for the given game session.
     * If a board already exists for the session, it will be replaced.
     */
    public void createBoardForSession(GameSession session) {
        if (session == null) return;
        removeBoard(session.getSessionId());

        Scoreboard scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        SessionBoard sb = new SessionBoard(session.getSessionId(), scoreboard);
        boards.put(session.getSessionId(), sb);

        // initial populate and assign to players
        sb.update();
        sb.start();

        for (UUID u : session.getPlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) p.setScoreboard(scoreboard);
        }
    }

    /**
     * Remove and unregister the scoreboard for the session (if present).
     */
    public void removeBoard(UUID sessionId) {
        SessionBoard sb = boards.remove(sessionId);
        if (sb == null) return;
        sb.cancel();

        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getScoreboard() == sb.scoreboard) {
                p.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());
            }
        });
    }

    /**
     * Update scoreboard for a single session immediately.
     */
    public void updateSession(UUID sessionId) {
        SessionBoard sb = boards.get(sessionId);
        if (sb != null) sb.update();
    }
}
