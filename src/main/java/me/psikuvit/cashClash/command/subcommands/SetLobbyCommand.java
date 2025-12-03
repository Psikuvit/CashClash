package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SetLobbyCommand extends AbstractArgCommand {
    public SetLobbyCommand() {
        super("setlobby", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can set the lobby spawn.</red>"); return true; }
        ArenaManager.getInstance().setServerLobbySpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "<green>Server lobby spawn set to your current location.</green>");
        return true;
    }
}

