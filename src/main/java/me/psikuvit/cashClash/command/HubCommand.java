package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Standalone /hub command - leaves the player's current game (if any) and puts them back in the
 * lobby with their lobby items, the same reset /cc leave and joining the server do.
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

        CashClashPlugin.getInstance().getLobbyManager().sendToLobby(player);
        Messages.send(player, "lobby.teleported-to-hub");
        return true;
    }
}
