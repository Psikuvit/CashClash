package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class StopCommand extends AbstractArgCommand {
    public StopCommand() {
        super("stop", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) {
            Messages.send(sender, "generic.not-in-game");
            return true;
        }

        session.end();
        Messages.send(sender, "admin.stop-sender-success");
        return true;
    }
}
