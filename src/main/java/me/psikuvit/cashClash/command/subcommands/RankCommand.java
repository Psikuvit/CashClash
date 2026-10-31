package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.rank.Rank;
import me.psikuvit.cashClash.rank.RankManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /cc rank set &lt;player&gt; &lt;owner|admin|none&gt; - gives or takes a staff rank, saved with the
 * player's data; /cc rank list - everyone with a rank. Owners can hand out admin; giving or
 * taking owner is console-only, so the first owner is set from the console.
 */
public class RankCommand extends AbstractArgCommand {

    private static final String NO_RANK = "none";

    public RankCommand() {
        super("rank", Collections.emptyList(), RankManager.OWNER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        RankManager rankManager = CashClashPlugin.getInstance().getRankManager();
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if (action.equals("list")) {
            sendList(sender, rankManager.getRankedPlayers());
            return true;
        }

        if (!action.equals("set") || args.length < 3) {
            Messages.send(sender, "ranks.usage");
            return true;
        }

        Rank rank = parseRank(args[2]);
        if (rank == null && !args[2].equalsIgnoreCase(NO_RANK)) {
            Messages.send(sender, "ranks.usage");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            Messages.send(sender, "ranks.player-unknown", "player", args[1]);
            return true;
        }
        String name = target.getName() != null ? target.getName() : args[1];

        Rank current = rankManager.getRank(target);
        if (current == rank) {
            Messages.send(sender, "ranks.unchanged", "player", name);
            return true;
        }
        if ((rank == Rank.OWNER || current == Rank.OWNER) && !(sender instanceof ConsoleCommandSender)) {
            Messages.send(sender, "ranks.owner-console-only");
            return true;
        }

        if (!rankManager.setRank(target, rank)) {
            Messages.send(sender, "ranks.save-failed", "player", name);
            return true;
        }

        Player online = target.getPlayer();
        if (rank == null) {
            Messages.send(sender, "ranks.removed", "player", name);
            if (online != null) Messages.send(online, "ranks.removed-you");
        } else {
            Messages.send(sender, "ranks.set", "player", name, "rank", rank.getDisplayName());
            if (online != null) Messages.send(online, "ranks.set-you", "rank", rank.getDisplayName());
        }
        return true;
    }

    private void sendList(CommandSender sender, Map<UUID, Rank> ranked) {
        if (ranked.isEmpty()) {
            Messages.send(sender, "ranks.list-empty");
            return;
        }

        List<String> entries = new ArrayList<>();
        ranked.forEach((uuid, rank) -> {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            entries.add((name != null ? name : uuid.toString()) + " (" + rank.getDisplayName() + ")");
        });
        Messages.send(sender, "ranks.list", "players", String.join(", ", entries));
    }

    private static Rank parseRank(String value) {
        for (Rank rank : Rank.values()) {
            if (rank.name().equalsIgnoreCase(value)) return rank;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        String partial = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        List<String> options = switch (args.length) {
            case 1 -> List.of("set", "list");
            case 2 -> args[0].equalsIgnoreCase("set")
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                    : List.of();
            case 3 -> args[0].equalsIgnoreCase("set") ? List.of("owner", "admin", NO_RANK) : List.of();
            default -> List.of();
        };
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial)).toList();
    }
}
