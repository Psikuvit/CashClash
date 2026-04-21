package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class StatsCommand extends AbstractArgCommand {
    public StatsCommand() {
        super("stats", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "stats.only-players-no-args");
                return true;
            }


            PlayerData data = PlayerDataManager.getInstance().getData(player.getUniqueId());

            Messages.send(player, "stats.title");
            Messages.send(player, "stats.wins", "wins", String.valueOf(data.getWins()));
            Messages.send(player, "stats.kills", "kills", String.valueOf(data.getKills()));
            Messages.send(player, "stats.deaths", "deaths", String.valueOf(data.getDeaths()));
            Messages.send(player, "stats.total-invested", "invested", String.valueOf(data.getTotalCoinsInvested()));

            return true;
        }

        // Admin actions: /cc stats reset <player>
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("cashclash.admin")) {
                Messages.send(sender, "generic.permission-reset-stats");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, "generic.player-not-found-or-offline");
                return true;
            }

            UUID tid = target.getUniqueId();
            PlayerData data = PlayerDataManager.getInstance().getData(tid);
            data.setKills(0);
            data.setDeaths(0);
            data.setWins(0);
            data.setTotalCoinsInvested(0L);

            Messages.send(sender, "stats.reset-success", "player_name", target.getName());

            return true;
        }

        Messages.send(sender, "stats.invalid-args");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String token = args[0].toLowerCase();
            if ("reset".startsWith(token)) {
                out.add("reset");
            }

            return out;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset")) {
                String token = args[1].toLowerCase();
                out.addAll(
                    Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(token))
                        .toList()
                );
            }

            return out;
        }

        return out;
    }
}
