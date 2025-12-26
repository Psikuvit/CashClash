package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.player.PlayerData;
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
                Messages.send(sender, "<red>Only players can use this command without arguments.</red>");
                return true;
            }


            PlayerData data = PlayerDataManager.getInstance().getData(player.getUniqueId());

            Messages.send(player, "<gold>=== Your Stats ===</gold>");
            Messages.send(player, "<yellow>Wins: <gray>" + data.getWins() + "</gray></yellow>");
            Messages.send(player, "<yellow>Kills: <gray>" + data.getKills() + "</gray></yellow>");
            Messages.send(player, "<yellow>Deaths: <gray>" + data.getDeaths() + "</gray></yellow>");
            Messages.send(player, "<yellow>Total Invested: <gray>$" + data.getTotalCoinsInvested() + "</gray></yellow>");

            return true;
        }

        // Admin actions: /cc stats reset <player>
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("cashclash.admin")) {
                Messages.send(sender, "<red>You don't have permission to reset player stats.</red>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, "<red>Player not found or not online.</red>");
                return true;
            }

            UUID tid = target.getUniqueId();
            PlayerData data = PlayerDataManager.getInstance().getData(tid);
            data.setKills(0);
            data.setDeaths(0);
            data.setWins(0);
            data.setTotalCoinsInvested(0L);

            Messages.send(sender, "<green>Player stats reset for: <yellow>" + target.getName() + "</yellow></green>");

            return true;
        }

        Messages.send(sender, "<red>Invalid stats command usage.</red>");
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
