package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class LeaveCommand extends AbstractArgCommand {
    public LeaveCommand() {
        super("leave", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            session.removePlayer(player);
            GameManager.getInstance().removePlayerFromSession(player);

            var lobbyLoc = ArenaManager.getInstance().getServerLobbySpawn();
            if (lobbyLoc != null) player.teleport(LocationUtils.clone(lobbyLoc));
            Messages.send(player, "gamestate.left-game");
            return true;
        }
        Messages.send(player, "generic.player-not-in-game");
        return true;
    }
}

