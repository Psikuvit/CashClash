package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class ForfeitCommand extends AbstractArgCommand {
    public ForfeitCommand() {
        super("forfeit", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "command.only-players"); return true; }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "generic.player-not-in-game");
            return true;
        }

        session.castForfeitVote(player);
        return true;
    }
}

