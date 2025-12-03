package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ForfeitCommand extends AbstractArgCommand {
    public ForfeitCommand() {
        super("forfeit", List.of(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use this command.</red>"); return true; }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            return true;
        }

        session.castForfeitVote(player);
        return true;
    }
}

