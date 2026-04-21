package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
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
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "generic.player-not-in-game");
            return true;
        }

        if (session.getState() != GameState.WAITING) {
            Messages.send(sender, "generic.game-started");
            return true;
        }

        if (session.getPlayers().isEmpty()) {
            Messages.send(sender, "admin.forcestart-no-players");
            return true;
        }

        session.cancelStartCountdown();

        session.start();
        Messages.broadcast(session.getPlayers(), "admin.game-force-started");
        Messages.send(sender, "admin.forcestart-success");

        return true;
    }
}


