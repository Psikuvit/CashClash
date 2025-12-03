package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.arena.ArenaManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LeaveCommand extends AbstractArgCommand {
    public LeaveCommand() {
        super("leave", List.of(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            session.removePlayer(player);
            GameManager.getInstance().removePlayerFromSession(player);

            var lobbyLoc = ArenaManager.getInstance().getServerLobbySpawn();
            if (lobbyLoc != null) player.teleport(LocationUtils.clone(lobbyLoc));

            Messages.send(player, "<green>You left the game.</green>");
            return true;
        }

        Messages.send(player, "<red>You're not in a game.</red>");
        return true;
    }
}

