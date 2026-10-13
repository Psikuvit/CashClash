package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * /cc setwaitinglobby - sets the shared waiting room queued players wait in to where the admin
 * is standing.
 */
public class SetWaitingLobbyCommand extends AbstractArgCommand {

    public SetWaitingLobbyCommand() {
        super("setwaitinglobby", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }
        CashClashPlugin.getInstance().getArenaManager().setWaitingLobbySpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "lobby.waiting-lobby-set");
        return true;
    }
}
