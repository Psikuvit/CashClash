package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Admin command to start a game with everyone in the queue immediately, skipping the countdown and
 * the minimum player count.
 */
public class ForceStartCommand extends AbstractArgCommand {

    public ForceStartCommand() {
        super("forcestart", List.of("fstart", "fs"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (CashClashPlugin.getInstance().getQueueManager().forceStart()) {
            Messages.send(sender, "queue.force-started");
        } else {
            Messages.send(sender, "queue.force-start-empty");
        }
        return true;
    }
}
