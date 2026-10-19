package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.lobby.QueueManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /cc pickmap <template>}: the queue's next game is played on that template's map.
 */
public class PickMapCommand extends AbstractArgCommand {

    public PickMapCommand() {
        super("pickmap", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            Messages.send(sender, "admin.pickmap-usage");
            return true;
        }

        String templateId = args[0];
        QueueManager queueManager = CashClashPlugin.getInstance().getQueueManager();
        if (!queueManager.hasReadyArena(templateId)) {
            Messages.send(sender, "admin.pickmap-unknown", "template", templateId);
            return true;
        }

        queueManager.setNextMap(templateId);
        Messages.send(sender, "admin.next-map-set", "template", templateId);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return List.of();

        String last = args[0].toLowerCase(Locale.ROOT);
        return CashClashPlugin.getInstance().getArenaManager().getAllArenas().values().stream()
                .filter(Arena::isReady)
                .map(Arena::getTemplateId)
                .filter(id -> id != null && id.toLowerCase(Locale.ROOT).startsWith(last))
                .distinct()
                .sorted()
                .toList();
    }
}
