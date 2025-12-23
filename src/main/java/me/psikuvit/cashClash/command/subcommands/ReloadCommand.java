package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Reload all configuration files.
 */
public class ReloadCommand extends AbstractArgCommand {

    public ReloadCommand() {
        super("reload", Collections.emptyList(), "cashclash.admin.reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        Messages.send(sender, "<yellow>Reloading CashClash configurations...</yellow>");

        try {
            ShopConfig.getInstance().reload();
            ItemsConfig.getInstance().reload();
            Messages.send(sender, "<green>✓ Successfully reloaded shop.yml and items.yml</green>");
            return true;
        } catch (Exception e) {
            Messages.send(sender, "<red>✗ Error reloading configs: " + e.getMessage() + "</red>");
            return false;
        }
    }
}

