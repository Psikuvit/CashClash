package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StopCommand extends AbstractArgCommand {
    public StopCommand() {
        super("stop", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "<red>You're not in a game.</red>");
            return true;
        }

        session.end();
        Messages.send(sender, "<green>Game ended.</green>");
        return true;
    }
}

