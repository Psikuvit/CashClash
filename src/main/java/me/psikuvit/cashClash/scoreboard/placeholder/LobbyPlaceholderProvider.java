package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides lobby-specific placeholders
 * Handles: player stats, online players, win rate, K/D ratio, etc.
 */
public class LobbyPlaceholderProvider implements PlaceholderProvider {

    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>();

    static {
        SUPPORTED_PLACEHOLDERS.add("player");
        SUPPORTED_PLACEHOLDERS.add("online");
        SUPPORTED_PLACEHOLDERS.add("max_online");
        SUPPORTED_PLACEHOLDERS.add("wins");
        SUPPORTED_PLACEHOLDERS.add("losses");
        SUPPORTED_PLACEHOLDERS.add("kills");
        SUPPORTED_PLACEHOLDERS.add("deaths");
        SUPPORTED_PLACEHOLDERS.add("total_coins_earned");
        SUPPORTED_PLACEHOLDERS.add("kd_ratio");
        SUPPORTED_PLACEHOLDERS.add("win_rate");
    }

    @Override
    public String getValue(String placeholder, Player player) {
        if (player == null) {
            return getDefaultValue(placeholder);
        }

        return switch (placeholder) {
            case "player" -> player.getName();
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_online" -> String.valueOf(Bukkit.getMaxPlayers());

            default -> getPlayerStatsValue(placeholder, player);
        };
    }

    @Override
    public boolean handles(String placeholder) {
        return SUPPORTED_PLACEHOLDERS.contains(placeholder);
    }

    private String getPlayerStatsValue(String placeholder, Player player) {
        PlayerData data = PlayerDataManager.getInstance().getOrLoadData(player.getUniqueId());

        if (data == null) {
            return getDefaultPlayerStatsValue(placeholder);
        }

        return switch (placeholder) {
            case "wins" -> String.valueOf(data.getWins());
            case "losses" -> String.valueOf(data.getLosses());
            case "kills" -> String.valueOf(data.getKills());
            case "deaths" -> String.valueOf(data.getDeaths());
            case "total_coins_earned" -> String.valueOf(data.getTotalCoinsEarned());
            case "kd_ratio" -> String.format("%.2f", data.getKills() / Math.max(1.0, data.getDeaths()));
            case "win_rate" -> String.format("%.1f%%",
                (data.getWins() / Math.max(1.0, data.getWins() + data.getLosses())) * 100);
            default -> null;
        };
    }

    private String getDefaultPlayerStatsValue(String placeholder) {
        return switch (placeholder) {
            case "wins", "losses", "kills", "deaths", "total_coins_earned" -> "0";
            case "kd_ratio" -> "0.00";
            case "win_rate" -> "0.0%";
            default -> null;
        };
    }

    private String getDefaultValue(String placeholder) {
        return switch (placeholder) {
            case "player" -> "Player";
            case "online", "max_online" -> "0";
            default -> getDefaultPlayerStatsValue(placeholder);
        };
    }
}

