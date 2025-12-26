package me.psikuvit.cashClash.scoreboard;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.player.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
 * Manages the lobby/hub scoreboard for players not in a game.
 * Shows player stats and server information.
 */
public class LobbyScoreboardManager {

    private static LobbyScoreboardManager instance;

    private final Map<UUID, Scoreboard> playerBoards;

    private LobbyScoreboardManager() {
        this.playerBoards = new HashMap<>();
    }

    public static LobbyScoreboardManager getInstance() {
        if (instance == null) {
            instance = new LobbyScoreboardManager();
        }
        return instance;
    }

    /**
     * Set the lobby scoreboard for a player.
     */
    public void setScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        playerBoards.put(uuid, board);

        updateScoreboard(player);
        player.setScoreboard(board);
    }

    /**
     * Remove the lobby scoreboard from a player.
     */
    public void removeScoreboard(UUID uuid) {
        Scoreboard board = playerBoards.remove(uuid);
        if (board == null) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());
        }
    }

    /**
     * Remove scoreboard for a player.
     */
    public void removeScoreboard(Player player) {
        if (player != null) {
            removeScoreboard(player.getUniqueId());
        }
    }


    /**
     * Update a specific player's lobby scoreboard.
     */
    public void updateScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Scoreboard board = playerBoards.get(uuid);
        if (board == null) {
            return;
        }

        String titleRaw = ConfigManager.getInstance().getLobbyScoreboardTitle();
        List<String> lines = ConfigManager.getInstance().getLobbyScoreboardLines();

        String objName = "lobby_" + uuid.toString().substring(0, 8);

        Objective oldObj = board.getObjective(objName);
        if (oldObj != null) {
            oldObj.unregister();
        }

        // Create new objective
        Component titleComp = Messages.parse(titleRaw);
        Objective objective = board.registerNewObjective(objName, Criteria.DUMMY, titleComp);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lines.size();
        int emptyCount = 0;
        for (String s : lines) {
            String line = fillPlaceholders(s, player);

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
    }

    /**
     * Fill placeholders for lobby scoreboard.
     */
    private String fillPlaceholders(String result, Player player) {
        result = result.replace("{player}", player.getName());

        result = result.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        result = result.replace("{max_online}", String.valueOf(Bukkit.getMaxPlayers()));

        PlayerData data = PlayerDataManager.getInstance().getOrLoadData(player.getUniqueId());
        if (data != null) {
            result = result.replace("{wins}", String.valueOf(data.getWins()));
            result = result.replace("{losses}", String.valueOf(data.getLosses()));
            result = result.replace("{kills}", String.valueOf(data.getKills()));
            result = result.replace("{deaths}", String.valueOf(data.getDeaths()));
            result = result.replace("{total_coins_earned}", String.valueOf(data.getTotalCoinsEarned()));

            // Calculate KDR
            double kdr = data.getDeaths() > 0 ? (double) data.getKills() / data.getDeaths() : data.getKills();
            result = result.replace("{kdr}", String.format("%.2f", kdr));

            // Calculate win rate
            int totalGames = data.getWins() + data.getLosses();
            double winRate = totalGames > 0 ? (double) data.getWins() / totalGames * 100 : 0;
            result = result.replace("{winrate}", String.format("%.1f", winRate));
        } else {
            result = result.replace("{wins}", "0");
            result = result.replace("{losses}", "0");
            result = result.replace("{kills}", "0");
            result = result.replace("{deaths}", "0");
            result = result.replace("{total_coins_earned}", "0");
            result = result.replace("{kdr}", "0.00");
            result = result.replace("{winrate}", "0.0");
        }

        return result;
    }

}

