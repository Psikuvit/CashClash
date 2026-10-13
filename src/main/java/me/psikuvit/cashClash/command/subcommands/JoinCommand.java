package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * /cc join - joins the queue, same as the arena villager's compass.
 */
public class JoinCommand extends AbstractArgCommand {
    public JoinCommand() {
        super("join", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }
        CashClashPlugin.getInstance().getQueueManager().join(player);
        return true;
    }
}
