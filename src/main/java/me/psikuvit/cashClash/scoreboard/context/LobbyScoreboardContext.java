package me.psikuvit.cashClash.scoreboard.context;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Scoreboard context for lobby players
 * Displays player stats and server information
 */
public class LobbyScoreboardContext implements ScoreboardContext {

    @Override
    public Component getTitle(Player player, GameSession session) {
        return Messages.parse(ConfigManager.getInstance().getLobbyScoreboardTitle());
    }

    @Override
    public List<String> getLines(Player player, GameSession session) {
        return ConfigManager.getInstance().getLobbyScoreboardLines();
    }

    @Override
    public String fillPlaceholders(String line, Player player, GameSession session) {
        // Player-based data
        line = line.replace("{player}", player.getName());
        line = line.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        line = line.replace("{max_online}", String.valueOf(Bukkit.getMaxPlayers()));

        // Player stats from storage
        PlayerData data = PlayerDataManager.getInstance().getOrLoadData(player.getUniqueId());
        if (data != null) {
            line = line.replace("{wins}", String.valueOf(data.getWins()));
            line = line.replace("{losses}", String.valueOf(data.getLosses()));
            line = line.replace("{kills}", String.valueOf(data.getKills()));
            line = line.replace("{deaths}", String.valueOf(data.getDeaths()));
            line = line.replace("{total_coins_earned}", String.valueOf(data.getTotalCoinsEarned()));
            line = line.replace("{kd_ratio}", String.format("%.2f", data.getKills() / Math.max(1.0, data.getDeaths())));
            line = line.replace("{win_rate}", String.format("%.1f%%", (data.getWins() / Math.max(1.0, data.getWins() + data.getLosses())) * 100));
        } else {
            line = line.replace("{wins}", "0");
            line = line.replace("{losses}", "0");
            line = line.replace("{kills}", "0");
            line = line.replace("{deaths}", "0");
            line = line.replace("{total_coins_earned}", "0");
            line = line.replace("{kd_ratio}", "0.00");
            line = line.replace("{win_rate}", "0.0%");
        }

        return line;
    }

    @Override
    public ContextType getContextType() {
        return ContextType.LOBBY;
    }
}

