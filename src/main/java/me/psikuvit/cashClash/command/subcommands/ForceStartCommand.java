package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Admin command to force start a game immediately, bypassing minimum player requirements.
 */
public class ForceStartCommand extends AbstractArgCommand {

    public ForceStartCommand() {
        super("forcestart", List.of("fstart", "fs"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "<red>You're not in a game session. Join an arena first.</red>");
            return true;
        }

        if (session.getState() != GameState.WAITING) {
            Messages.send(sender, "<red>The game has already started!</red>");
            return true;
        }

        if (session.getPlayers().isEmpty()) {
            Messages.send(sender, "<red>Cannot force start with no players.</red>");
            return true;
        }

        session.cancelStartCountdown();

        session.start();
        Messages.broadcast(session.getPlayers(), "<gold><bold>Game force started by an admin!</bold></gold>");
        Messages.send(sender, "<green>Game force started successfully.</green>");

        return true;
    }
}

