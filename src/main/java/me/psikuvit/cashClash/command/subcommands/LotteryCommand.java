package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.CashQuakeManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Command for players to enter the lottery Cash Quake event.
 * Usage: /cc lottery
 */
public class LotteryCommand extends AbstractArgCommand {

    public LotteryCommand() {
        super("lottery", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            return true;
        }

        CashQuakeManager cqm = session.getCashQuakeManager();
        if (cqm == null) {
            Messages.send(player, "<red>Cash Quake events are not available.</red>");
            return true;
        }

        cqm.enterLottery(player);
        return true;
    }
}

