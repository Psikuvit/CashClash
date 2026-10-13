package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /cc hubbreak [on|off] - turns breaking hub blocks on or off for every player outside a game.
 * With no argument it flips the current setting.
 */
public class HubBreakCommand extends AbstractArgCommand {

    public HubBreakCommand() {
        super("hubbreak", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();

        boolean enabled;
        if (args.length == 0) {
            enabled = !cfg.isHubBlockBreaking();
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on" -> enabled = true;
                case "off" -> enabled = false;
                default -> {
                    Messages.send(sender, "admin.hubbreak-usage");
                    return true;
                }
            }
        }

        cfg.setHubBlockBreaking(enabled);
        Messages.send(sender, enabled ? "admin.hubbreak-on" : "admin.hubbreak-off");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String last = args[0].toLowerCase(Locale.ROOT);
        return List.of("on", "off").stream().filter(s -> s.startsWith(last)).toList();
    }
}
