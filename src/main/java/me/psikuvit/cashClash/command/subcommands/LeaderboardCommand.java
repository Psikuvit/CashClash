package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.player.LeaderboardManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Displays cached leaderboards (wins, playtime, coins earned).
 * Usage: /cc leaderboard [wins|playtime|coins]
 */
public class LeaderboardCommand extends AbstractArgCommand {

    public LeaderboardCommand() {
        super("leaderboard", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        LeaderboardManager.LeaderboardType type = null;
        if (args.length >= 1) {
            type = LeaderboardManager.LeaderboardType.fromString(args[0]);
            if (type == null) {
                Messages.send(sender, "leaderboard.usage");
                return true;
            }
        }

        if (type == null) {
            Messages.send(sender, "leaderboard.header-all");
            showBoard(sender, LeaderboardManager.LeaderboardType.WINS);
            showBoard(sender, LeaderboardManager.LeaderboardType.PLAY_TIME);
            showBoard(sender, LeaderboardManager.LeaderboardType.COINS_EARNED);
        } else {
            Messages.send(sender, "leaderboard.header", "board", type.getConfigKey());
            showBoard(sender, type);
        }

        return true;
    }

    private void showBoard(CommandSender sender, LeaderboardManager.LeaderboardType type) {
        List<PlayerData> board = CashClashPlugin.getInstance().getLeaderboardManager().getBoard(type);
        if (board.isEmpty()) {
            Messages.send(sender, "leaderboard.empty", "board", type.getConfigKey());
            return;
        }

        for (int i = 0; i < board.size(); i++) {
            PlayerData data = board.get(i);
            String name = resolveName(data);
            String value = switch (type) {
                case WINS -> String.valueOf(data.getWins());
                case PLAY_TIME -> formatPlaytime(data.getPlaytimeMillis());
                case COINS_EARNED -> String.format("%,d", data.getTotalCoinsEarned());
            };
            Messages.send(sender, "leaderboard.entry",
                    "rank", String.valueOf(i + 1),
                    "player_name", name,
                    "value", value);
        }
    }

    private String formatPlaytime(long millis) {
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        return hours + "h " + minutes + "m";
    }

    private String resolveName(PlayerData data) {
        var online = Bukkit.getPlayer(data.getUuid());
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(data.getUuid());
        String name = offline.getName();
        return name != null ? name : data.getUuid().toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("wins", "playtime", "coins")
                    .filter(s -> s.startsWith(token))
                    .toList();
        }
        return Collections.emptyList();
    }
}
