package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.scoreboard.ScoreboardProvider;
import me.psikuvit.cashClash.scoreboard.context.ContextType;
import me.psikuvit.cashClash.scoreboard.context.ScoreboardContext;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified scoreboard manager for both lobby and game scoreboards.
 * Automatically switches between contexts based on player state (lobby or game).
 * Uses ScoreboardProvider to auto-detect gamemode and context.
 */
public class ScoreboardManager {

    private static ScoreboardManager instance;

    // Map of playerUUID -> (sessionId -> Scoreboard) for game scoreboards
    private final Map<UUID, Map<UUID, Scoreboard>> gamePlayerBoards;
    // Map of playerUUID -> Scoreboard for lobby scoreboards
    private final Map<UUID, Scoreboard> lobbyPlayerBoards;
    // Map of sessionId -> update task
    private final Map<UUID, BukkitTask> sessionUpdateTasks;
    // Map of playerUUID -> current context type (for detecting changes)
    private final Map<UUID, ContextType> playerContexts;

    private ScoreboardManager() {
        this.gamePlayerBoards = new HashMap<>();
        this.lobbyPlayerBoards = new HashMap<>();
        this.sessionUpdateTasks = new HashMap<>();
        this.playerContexts = new HashMap<>();
    }

    public static ScoreboardManager getInstance() {
        if (instance == null) {
            instance = new ScoreboardManager();
        }
        return instance;
    }

    /**
     * Set the scoreboard for a player (auto-detects lobby vs game and gamemode)
     */
    public void setScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Detect context using ScoreboardProvider
        ScoreboardContext context = ScoreboardProvider.getContext(player);
        ContextType contextType = context.getContextType();

        // Check if context changed
        ContextType previousContext = playerContexts.get(player.getUniqueId());
        if (previousContext != null && previousContext == contextType) {
            // Same context, just update
            updatePlayerScoreboard(player);
            return;
        }

        // Context changed - remove old scoreboard and create new one
        removeScoreboard(player);

        if (contextType == ContextType.LOBBY) {
            createLobbyScoreboard(player);
        } else {
            createGameScoreboard(player);
        }

        playerContexts.put(player.getUniqueId(), contextType);
    }

    /**
     * Create scoreboard for lobby players
     */
    private void createLobbyScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        lobbyPlayerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);

        updatePlayerScoreboard(player);
    }

    /**
     * Create scoreboard for game players
     */
    private void createGameScoreboard(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null || !player.isOnline()) {
            return;
        }

        // Remove from lobby scoreboard
        removeScoreboard(player);

        Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();

        // Setup team colors
        TeamColorUtils.assignPlayersToTeams(board, session);

        // Store in game boards
        UUID sessionId = session.getSessionId();
        gamePlayerBoards.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(sessionId, board);

        player.setScoreboard(board);

        // Update tab list
        TabListManager.getInstance().setPlayerToSession(player, session);

        // Update immediately
        updatePlayerScoreboard(player);

        // Start update task if not already running for this session
        if (!sessionUpdateTasks.containsKey(sessionId)) {
            BukkitTask task = SchedulerUtils.runTaskTimerAsync(() -> updateSessionScoreboards(sessionId), 0L, 20L);
            sessionUpdateTasks.put(sessionId, task);
        }
    }

    /**
     * Remove scoreboard from a player
     */
    public void removeScoreboard(Player player) {
        if (player == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Remove lobby scoreboard
        Scoreboard lobbyBoard = lobbyPlayerBoards.remove(playerUuid);
        if (lobbyBoard != null) {
            lobbyBoard.getObjectives().forEach(Objective::unregister);
        }

        // Remove game scoreboards
        Map<UUID, Scoreboard> gameBoards = gamePlayerBoards.remove(playerUuid);
        if (gameBoards != null) {
            gameBoards.values().forEach(board -> board.getObjectives().forEach(Objective::unregister));
        }

        // Remove context
        playerContexts.remove(playerUuid);

        // Reset to main scoreboard
        if (player.isOnline()) {
            player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());
        }
    }

    /**
     * Update scoreboard for a specific player
     */
    public void updatePlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        ScoreboardContext context = ScoreboardProvider.getContext(player);

        Scoreboard board = null;
        if (session != null) {
            Map<UUID, Scoreboard> playerBoards = gamePlayerBoards.get(player.getUniqueId());
            if (playerBoards != null) {
                board = playerBoards.get(session.getSessionId());
            }
        } else {
            board = lobbyPlayerBoards.get(player.getUniqueId());
        }

        if (board == null) {
            return;
        }

        updateScoreboard(player, board, context, session);
    }

    /**
     * Update all scoreboards for a session
     */
    private void updateSessionScoreboards(UUID sessionId) {
        GameSession session = GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);

        if (session == null) {
            cancelUpdateTask(sessionId);
            return;
        }

        for (UUID playerUuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            updatePlayerScoreboard(player);
        }
    }

    /**
     * Internal: Update a specific scoreboard
     */
    private void updateScoreboard(Player player, Scoreboard board, ScoreboardContext context, GameSession session) {
        Component title = context.getTitle(player, session);
        List<String> lines = context.getLines(player, session);

        String objName = "scoreboard_" + player.getUniqueId().toString().substring(0, 8);

        Objective oldObj = board.getObjective(objName);
        if (oldObj != null) {
            oldObj.unregister();
        }

        Objective objective = board.registerNewObjective(objName, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lines.size();
        int emptyCount = 0;
        for (String line : lines) {
            String filled = context.fillPlaceholders(line, player, session);

            if (filled.length() > 40) {
                filled = filled.substring(0, 40);
            }

            if (filled.isEmpty()) {
                filled = "§" + Integer.toHexString(emptyCount % 16);
                emptyCount++;
            }

            Score scoreObj = objective.getScore(filled);
            scoreObj.customName(Messages.parse(filled));
            scoreObj.setScore(score--);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /**
     * Remove all scoreboards for a session
     */
    public void removeBoard(UUID sessionId) {
        cancelUpdateTask(sessionId);

        for (Map<UUID, Scoreboard> playerBoards : gamePlayerBoards.values()) {
            Scoreboard board = playerBoards.remove(sessionId);
            if (board != null) {
                board.getObjectives().forEach(Objective::unregister);
            }
        }
    }

    /**
     * Cancel update task for a session
     */
    private void cancelUpdateTask(UUID sessionId) {
        BukkitTask task = sessionUpdateTasks.remove(sessionId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Shutdown all scoreboards
     */
    public void shutdown() {
        sessionUpdateTasks.values().forEach(BukkitTask::cancel);
        sessionUpdateTasks.clear();

        gamePlayerBoards.forEach((playerUuid, boards) ->
                boards.values().forEach(board -> board.getObjectives().forEach(Objective::unregister))
        );
        gamePlayerBoards.clear();

        lobbyPlayerBoards.values().forEach(board -> board.getObjectives().forEach(Objective::unregister));
        lobbyPlayerBoards.clear();

        playerContexts.clear();
    }

    // Deprecated methods for backward compatibility
    public void createBoardForSession(GameSession session) {
        for (UUID playerUuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                setScoreboard(player);
            }
        }
    }
}

