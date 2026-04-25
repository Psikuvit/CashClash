package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SetLobbyCommand extends AbstractArgCommand {
    public SetLobbyCommand() {
        super("setlobby", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }
        ArenaManager.getInstance().setServerLobbySpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "lobby.server-lobby-set");
        return true;
    }
}
