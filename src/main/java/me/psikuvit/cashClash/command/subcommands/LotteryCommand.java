package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Command for players to enter the lottery Cash Quake event.
 * Usage: /cc lottery
 */
@Deprecated(forRemoval = true)
public class LotteryCommand extends AbstractArgCommand {

    public LotteryCommand() {
        super("lottery", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "generic.player-not-in-game");
            return true;
        }

        /**CashQuakeManager cqm = session.getCashQuakeManager();
        if (cqm == null) {
            Messages.send(player, "generic.cashquake-unavailable");
            return true;
        }

        cqm.enterLottery(player);*/
        return true;
    }
}

