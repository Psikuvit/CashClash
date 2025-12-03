package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StatsCommand extends AbstractArgCommand {
    public StatsCommand() {
        super("stats", List.of(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use this command.</red>"); return true; }
        Messages.send(player, "<gold>=== Your Stats ===</gold>");
        Messages.send(player, "<yellow>Games Played: <gray>0</gray></yellow>");
        Messages.send(player, "<yellow>Wins: <gray>0</gray></yellow>");
        Messages.send(player, "<yellow>Kills: <gray>0</gray></yellow>");
        return true;
    }
}

