package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Reload all configuration files.
 * Usage: /cc reload [config|shop|items|all]
 */
public class ReloadCommand extends AbstractArgCommand {

    public ReloadCommand() {
        super("reload", Collections.emptyList(), "cashclash.admin.reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        Messages.send(sender, "<yellow>Reloading CashClash configurations...</yellow>");

        try {
            int reloaded;

            switch (target) {
                case "config" -> {
                    CashClashPlugin.getInstance().reloadConfig();
                    ConfigManager.getInstance().reload();
                    reloaded = 1;
                    Messages.send(sender, "<green>✓ Reloaded config.yml</green>");
                }
                case "shop" -> {
                    ShopConfig.getInstance().reload();
                    reloaded = 1;
                    Messages.send(sender, "<green>✓ Reloaded shop.yml</green>");
                }
                case "items" -> {
                    ItemsConfig.getInstance().reload();
                    reloaded = 1;
                    Messages.send(sender, "<green>✓ Reloaded items.yml</green>");
                }
                case "all" -> {
                    CashClashPlugin.getInstance().reloadConfig();
                    ConfigManager.getInstance().reload();
                    ShopConfig.getInstance().reload();
                    ItemsConfig.getInstance().reload();
                    reloaded = 3;
                    Messages.send(sender, "<green>✓ Reloaded all configuration files</green>");
                }
                default -> {
                    Messages.send(sender, "<red>Unknown config: " + target + "</red>");
                    Messages.send(sender, "<gray>Usage: /cc reload [config|shop|items|all]</gray>");
                    return false;
                }
            }

            Messages.send(sender, "<green>Successfully reloaded " + reloaded + " configuration file(s)!</green>");

            // Log to console
            CashClashPlugin.getInstance().getLogger().info(
                sender.getName() + " reloaded " + target + " configuration(s)"
            );

            return true;
        } catch (Exception e) {
            Messages.send(sender, "<red>✗ Error reloading configs: " + e.getMessage() + "</red>");
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Config reload error", e);
            return false;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String option : List.of("all", "config", "shop", "items")) {
                if (option.startsWith(input)) {
                    completions.add(option);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}

