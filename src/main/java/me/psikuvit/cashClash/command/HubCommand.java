package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Standalone /hub command - leaves the player's current game (if any) and teleports them to
 * the server lobby spawn, same destination /cc leave uses.
 */
public class HubCommand extends Command {

    public HubCommand() {
        super("hub");
        setDescription("Teleport to the server hub");
        setUsage("/<command>");
        setAliases(List.of("lobby"));
        setPermission("cashclash.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session != null) {
            session.removePlayer(player);
            CashClashPlugin.getInstance().getGameManager().removePlayerFromSession(player);
        }

        var lobbyLoc = CashClashPlugin.getInstance().getArenaManager().getServerLobbySpawn();
        if (lobbyLoc != null) player.teleport(LocationUtils.clone(lobbyLoc));

        Messages.send(player, "lobby.teleported-to-hub");
        return true;
    }
}
