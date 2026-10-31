package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.rank.RankManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /cc resetstats - wipes every player's stats (wins, losses, kills, deaths, coins, playtime) on
 * the server. Owners only, and only after clicking YES on the confirmation it sends, which
 * expires after {@value #CONFIRM_SECONDS} seconds.
 */
public class ResetStatsCommand extends AbstractArgCommand {

    private static final int CONFIRM_SECONDS = 10;

    // Sender name -> when their confirmation expires (epoch millis)
    private final Map<String, Long> pendingConfirmations = new HashMap<>();

    public ResetStatsCommand() {
        super("resetstats", Collections.emptyList(), RankManager.OWNER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "confirm" -> confirm(sender);
            case "cancel" -> {
                if (pendingConfirmations.remove(sender.getName()) != null) {
                    Messages.send(sender, "ranks.reset-stats-cancelled");
                } else {
                    Messages.send(sender, "ranks.reset-stats-none-pending");
                }
            }
            default -> requestConfirmation(sender);
        }
        return true;
    }

    private void requestConfirmation(CommandSender sender) {
        long expiresAt = System.currentTimeMillis() + CONFIRM_SECONDS * 1000L;
        pendingConfirmations.put(sender.getName(), expiresAt);
        Messages.send(sender, "ranks.reset-stats-confirm", "seconds", String.valueOf(CONFIRM_SECONDS));

        SchedulerUtils.runTaskLater(() -> {
            if (pendingConfirmations.remove(sender.getName(), expiresAt)) {
                Messages.send(sender, "ranks.reset-stats-expired");
            }
        }, CONFIRM_SECONDS * 20L);
    }

    private void confirm(CommandSender sender) {
        Long expiresAt = pendingConfirmations.remove(sender.getName());
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            Messages.send(sender, "ranks.reset-stats-none-pending");
            return;
        }

        CashClashPlugin plugin = CashClashPlugin.getInstance();
        int count = plugin.getPlayerDataManager().resetAllStats();
        SchedulerUtils.runTaskAsync(plugin.getLeaderboardManager()::refresh);

        Messages.send(sender, "ranks.reset-stats-done", "count", String.valueOf(count));
        List<UUID> everyone = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        Messages.broadcast(everyone, "ranks.reset-stats-broadcast", "player", sender.getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1 || !pendingConfirmations.containsKey(sender.getName())) return Collections.emptyList();
        String partial = args[0].toLowerCase(Locale.ROOT);
        return List.of("confirm", "cancel").stream().filter(s -> s.startsWith(partial)).toList();
    }
}
