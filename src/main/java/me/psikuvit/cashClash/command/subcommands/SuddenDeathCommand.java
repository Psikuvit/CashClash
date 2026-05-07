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
 * Admin testing command to force the current game into sudden death.
 */
public class SuddenDeathCommand extends AbstractArgCommand {

    public SuddenDeathCommand() {
        super("suddendeath", List.of("sd", "forcesuddendeath"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "generic.not-in-game");
            return true;
        }

        if (session.getState() != GameState.COMBAT) {
            sender.sendMessage(Messages.parse("<red>Sudden death can only be forced during combat.</red>"));
            return true;
        }

        if (session.getGamemode() == null || !session.getGamemode().forceSuddenDeathForTesting()) {
            sender.sendMessage(Messages.parse("<yellow>Sudden death is already active or this gamemode does not support it.</yellow>"));
            return true;
        }

        sender.sendMessage(Messages.parse("<green>Forced sudden death for this match.</green>"));
        return true;
    }
}
