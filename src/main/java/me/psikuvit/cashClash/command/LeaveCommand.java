package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Standalone /leave command - walks out of the player's game exactly as disconnecting would
 * (the last enemy to hit them gets the kill, their payouts wait for them, their spot is kept for
 * /rejoin), then puts them in the hub.
 */
public class LeaveCommand extends Command {

    public LeaveCommand() {
        super("leave");
        setDescription("Leave your Cash Clash game");
        setUsage("/<command>");
        setPermission("cashclash.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        CashClashPlugin plugin = CashClashPlugin.getInstance();
        GameSession session = plugin.getGameManager().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "rejoin.not-in-game");
            return true;
        }

        plugin.getMythicItemManager().cleanupOnQuit(player);
        boolean kept = plugin.getRejoinManager().leaveGame(player, session, false);

        plugin.getLobbyManager().sendToLobby(player);
        if (kept) {
            Messages.send(player, "rejoin.left-game",
                    "timeout_seconds", String.valueOf(plugin.getConfigManager().getRejoinTimeoutSeconds()));
        } else {
            Messages.send(player, "rejoin.left-game-for-good");
        }
        return true;
    }
}
