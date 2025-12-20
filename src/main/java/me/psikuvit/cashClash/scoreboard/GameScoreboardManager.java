package me.psikuvit.cashClash.scoreboard;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.TabListManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
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
 * Manages in-game scoreboards for players in active game sessions.
 * Shows round data, timer, phase, coins, lives, etc.
 */
public class GameScoreboardManager {

    private static GameScoreboardManager instance;

    // Map of sessionId -> Map of playerUUID -> Scoreboard
    private final Map<UUID, Map<UUID, Scoreboard>> sessionBoards;
    private final Map<UUID, BukkitTask> sessionTasks;

    private GameScoreboardManager() {
        this.sessionBoards = new HashMap<>();
        this.sessionTasks = new HashMap<>();
    }

    public static GameScoreboardManager getInstance() {
        if (instance == null) {
            instance = new GameScoreboardManager();
        }
        return instance;
    }

    /**
     * Create scoreboards for all players in a game session.
     */
    public void createForSession(GameSession session) {
        if (session == null) {
            return;
        }

        UUID sessionId = session.getSessionId();

        removeForSession(sessionId);

        Map<UUID, Scoreboard> playerBoards = new HashMap<>();
        sessionBoards.put(sessionId, playerBoards);

        for (UUID playerUuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            LobbyScoreboardManager.getInstance().removeScoreboard(playerUuid);

            Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();

            TeamColorUtils.assignPlayersToTeams(board, session);

            playerBoards.put(playerUuid, board);
            player.setScoreboard(board);

            // Update tab for player
            TabListManager.getInstance().setPlayerToSession(player, session);
        }

        BukkitTask task = SchedulerUtils.runTaskTimerAsync(() -> updateSession(sessionId), 0L, 20L);
        sessionTasks.put(sessionId, task);

        updateSession(sessionId);
    }

    /**
     * Add a player to an existing session's scoreboard.
     */
    public void addPlayer(GameSession session, Player player) {
        if (session == null || player == null) {
            return;
        }

        UUID sessionId = session.getSessionId();
        Map<UUID, Scoreboard> playerBoards = sessionBoards.get(sessionId);

        if (playerBoards == null) {
            createForSession(session);
            return;
        }

        // Remove from lobby scoreboard
        LobbyScoreboardManager.getInstance().removeScoreboard(player);

        Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();

        // Setup team colors for tab list and nametag coloring
        TeamColorUtils.assignPlayersToTeams(board, session);

        playerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);

        // Update tab list
        TabListManager.getInstance().setPlayerToSession(player, session);

        // Update immediately
        updatePlayerBoard(session, player.getUniqueId(), board);
    }

    /**
     * Remove a player from a session's scoreboard and give them the lobby scoreboard.
     */
    public void removePlayer(UUID sessionId, Player player) {
        if (player == null) {
            return;
        }

        Map<UUID, Scoreboard> playerBoards = sessionBoards.get(sessionId);
        if (playerBoards != null) {
            Scoreboard board = playerBoards.remove(player.getUniqueId());
            if (board != null) {
                // Unregister objective
                board.getObjectives().forEach(Objective::unregister);
            }
        }

        // Reset to main scoreboard
        player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());

        // Give lobby scoreboard
        LobbyScoreboardManager.getInstance().setScoreboard(player);

        // Reset tab list to lobby display
        TabListManager.getInstance().setPlayerToLobby(player);
    }

    /**
     * Remove all scoreboards for a session.
     */
    public void removeForSession(UUID sessionId) {
        // Cancel update task
        BukkitTask task = sessionTasks.remove(sessionId);
        if (task != null) {
            task.cancel();
        }

        // Remove all player boards
        Map<UUID, Scoreboard> playerBoards = sessionBoards.remove(sessionId);
        if (playerBoards == null) {
            return;
        }

        for (Map.Entry<UUID, Scoreboard> entry : playerBoards.entrySet()) {
            UUID playerUuid = entry.getKey();
            Scoreboard board = entry.getValue();

            board.getObjectives().forEach(Objective::unregister);

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                LobbyScoreboardManager.getInstance().setScoreboard(player);
                TabListManager.getInstance().setPlayerToLobby(player);
            }
        }

        playerBoards.clear();
    }

    /**
     * Update all scoreboards for a session.
     */
    public void updateSession(UUID sessionId) {
        GameSession session = GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);

        if (session == null) {
            removeForSession(sessionId);
            return;
        }

        Map<UUID, Scoreboard> playerBoards = sessionBoards.get(sessionId);
        if (playerBoards == null) {
            return;
        }

        for (Map.Entry<UUID, Scoreboard> entry : playerBoards.entrySet()) {
            UUID playerUuid = entry.getKey();
            Scoreboard board = entry.getValue();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            updatePlayerBoard(session, playerUuid, board);
        }
    }

    /**
     * Update a specific player's scoreboard.
     */
    private void updatePlayerBoard(GameSession session, UUID playerUuid, Scoreboard board) {
        if (session == null || board == null) {
            return;
        }

        String titleRaw = ConfigManager.getInstance().getGameScoreboardTitle();
        List<String> lines = ConfigManager.getInstance().getGameScoreboardLines();

        String objName = "game_" + playerUuid.toString().substring(0, 8);

        Objective oldObj = board.getObjective(objName);
        if (oldObj != null) {
            oldObj.unregister();
        }

        Component titleComp = Messages.parse(fillPlaceholders(titleRaw, session, playerUuid));

        // Create new objective
        Objective objective = board.registerNewObjective(objName, Criteria.DUMMY, titleComp);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lines.size();
        int emptyCount = 0;
        for (String s : lines) {
            String line = fillPlaceholders(s, session, playerUuid);

            if (line.length() > 40) {
                line = line.substring(0, 40);
            }

            // Make empty lines unique by adding invisible color codes
            if (line.isEmpty()) {
                line = "§" + Integer.toHexString(emptyCount % 16);
                emptyCount++;
            }

            Score scoreComp = objective.getScore(line);
            scoreComp.customName(Messages.parse(line));
            scoreComp.setScore(score--);
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline() && player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /**
     * Fill placeholders for game scoreboard.
     */
    private String fillPlaceholders(String result, GameSession session, UUID viewerUuid) {
        result = result.replace("{round}", String.valueOf(session.getCurrentRound()));

        GameState state = session.getState();
        result = result.replace("{state}", getPhase(state));
        result = result.replace("{phase}", getPhase(state));
        result = result.replace("{phase_number}", String.valueOf(session.getCurrentRound()));

        int timeRemaining = session.getTimeRemaining();

        result = result.replace("{time}", formatTime(timeRemaining));
        result = result.replace("{time_seconds}", String.valueOf(timeRemaining));

        result = result.replace("{team1_coins}", formatCoins(session.getTeam1Coins()));
        result = result.replace("{team2_coins}", formatCoins(session.getTeam2Coins()));

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) {
            Team playerTeam = session.getPlayerTeam(viewer);
            if (playerTeam != null) {
                result = result.replace("{your_team}", String.valueOf(playerTeam.getTeamNumber()));
                long teamCoins = playerTeam.getTeamNumber() == 1 ? session.getTeam1Coins() : session.getTeam2Coins();
                result = result.replace("{your_team_coins}", formatCoins(teamCoins));

                Team enemyTeam = session.getOpposingTeam(playerTeam);
                result = result.replace("{enemy_team}", String.valueOf(enemyTeam.getTeamNumber()));
                long enemyCoins = enemyTeam.getTeamNumber() == 1 ? session.getTeam1Coins() : session.getTeam2Coins();
                result = result.replace("{enemy_team_coins}", formatCoins(enemyCoins));
            } else {
                result = result.replace("{your_team}", "?");
                result = result.replace("{your_team_coins}", "0");
                result = result.replace("{enemy_team}", "?");
                result = result.replace("{enemy_team_coins}", "0");
            }
        }

        CashClashPlayer ccp = session.getCashClashPlayer(viewerUuid);
        if (ccp != null) {
            result = result.replace("{player_coins}", formatCoins(ccp.getCoins()));
            result = result.replace("{player_kills}", String.valueOf(ccp.getTotalKills()));
            result = result.replace("{player_lives}", String.valueOf(ccp.getLives()));
            result = result.replace("{player_deaths}", String.valueOf(ccp.getDeathsThisRound()));
            result = result.replace("{kill_streak}", String.valueOf(ccp.getKillStreak()));
        } else {
            result = result.replace("{player_coins}", "0");
            result = result.replace("{player_kills}", "0");
            result = result.replace("{player_lives}", "0");
            result = result.replace("{player_deaths}", "0");
            result = result.replace("{kill_streak}", "0");
        }

        RoundData roundData = session.getCurrentRoundData();
        if (roundData != null) {
            result = result.replace("{round_kills}", String.valueOf(roundData.getKills(viewerUuid)));

            // Team alive counts
            Team team1 = session.getTeam1();
            Team team2 = session.getTeam2();
            long team1Alive = team1.getPlayers().stream().filter(roundData::isAlive).count();
            long team2Alive = team2.getPlayers().stream().filter(roundData::isAlive).count();
            result = result.replace("{team1_alive}", String.valueOf(team1Alive));
            result = result.replace("{team2_alive}", String.valueOf(team2Alive));

            // Player's team alive count
            if (viewer != null) {
                Team playerTeam = session.getPlayerTeam(viewer);
                if (playerTeam != null) {
                    long yourAlive = playerTeam.getPlayers().stream().filter(roundData::isAlive).count();
                    long enemyAlive = session.getOpposingTeam(playerTeam).getPlayers().stream().filter(roundData::isAlive).count();
                    result = result.replace("{your_team_alive}", String.valueOf(yourAlive));
                    result = result.replace("{enemy_team_alive}", String.valueOf(enemyAlive));
                } else {
                    result = result.replace("{your_team_alive}", "0");
                    result = result.replace("{enemy_team_alive}", "0");
                }
            }
        } else {
            result = result.replace("{round_kills}", "0");
            result = result.replace("{team1_alive}", "0");
            result = result.replace("{team2_alive}", "0");
            result = result.replace("{your_team_alive}", "0");
            result = result.replace("{enemy_team_alive}", "0");
        }

        result = result.replace("{players}", String.valueOf(session.getPlayers().size()));

        return result;
    }

    /**
     * Get the phase type (Shopping or Combat).
     */
    private String getPhase(GameState state) {
        if (state == null) {
            return "Unknown";
        }
        String name = state.name();
        if (name.contains("SHOPPING")) {
            return "Shopping";
        } else if (name.contains("COMBAT")) {
            return "Combat";
        } else if (name.equals("WAITING")) {
            return "Waiting";
        } else {
            return "Ending";
        }
    }

    /**
     * Format time in MM:SS format.
     */
    private String formatTime(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Format coins with commas.
     */
    private String formatCoins(long coins) {
        return String.format("%,d", coins);
    }

    /**
     * Shutdown all scoreboards.
     */
    public void shutdown() {
        // Cancel all tasks
        sessionTasks.values().forEach(BukkitTask::cancel);
        sessionTasks.clear();

        // Remove all boards
        for (UUID sessionId : sessionBoards.keySet()) {
            removeForSession(sessionId);
        }
        sessionBoards.clear();
    }
}

