package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Standalone /start command - anyone, op or not, can start the queue's game straight away with
 * everyone queued, while {@code start-command.enabled} is on.
 */
public class StartCommand extends Command {

    public StartCommand() {
        super("start");
        setDescription("Start the Cash Clash game with everyone in the queue");
        setUsage("/<command>");
        setPermission("cashclash.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String @NonNull [] args) {
        CashClashPlugin plugin = CashClashPlugin.getInstance();
        if (!plugin.getConfigManager().isStartCommandEnabled()) {
            Messages.send(sender, "queue.start-disabled");
            return true;
        }

        if (plugin.getQueueManager().forceStart()) {
            Messages.send(sender, "queue.force-started");
        } else {
            Messages.send(sender, "queue.force-start-empty");
        }
        return true;
    }
}
