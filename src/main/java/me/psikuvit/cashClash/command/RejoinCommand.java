package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.lobby.QueueManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Standalone /rejoin command - puts a player who left a game (with /leave or by disconnecting)
 * back into it, while it's still going and their spot is still kept.
 */
public class RejoinCommand extends Command {

    public RejoinCommand() {
        super("rejoin");
        setDescription("Go back to the Cash Clash game you left");
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
        if (plugin.getGameManager().getPlayerSession(player) != null) {
            Messages.send(player, "rejoin.already-in-game");
            return true;
        }

        RejoinManager rejoinManager = plugin.getRejoinManager();
        boolean pending = rejoinManager.hasPendingRejoin(player.getUniqueId());
        if (pending) {
            if (plugin.getQueueManager().leave(player) == QueueManager.LeaveResult.PARTY_MEMBER) return true;
            plugin.getPracticeRangeManager().remove(player);
            player.getInventory().clear();
        }

        RejoinManager.RejoinResult result = rejoinManager.rejoinViaCommand(player);
        if (pending && result != RejoinManager.RejoinResult.REJOINED) {
            plugin.getLobbyManager().giveLobbyItems(player);
        }

        switch (result) {
            case REJOINED -> { }
            case NOTHING_TO_REJOIN -> Messages.send(player, "rejoin.nothing-to-rejoin");
            case GAME_OVER -> Messages.send(player, "rejoin.game-already-over");
            case TOO_LATE -> Messages.send(player, "rejoin.too-late");
        }
        return true;
    }
}
