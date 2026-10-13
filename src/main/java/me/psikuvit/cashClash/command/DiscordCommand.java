package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Standalone /discord command - sends the server's Discord invite ({@code hub.discord-link} in
 * config.yml) as a clickable link.
 */
public class DiscordCommand extends Command {

    public DiscordCommand() {
        super("discord");
        setDescription("Get the server's Discord invite");
        setUsage("/<command>");
        setPermission("cashclash.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String @NonNull [] args) {
        Messages.send(sender, "lobby.discord-link", "link", CashClashPlugin.getInstance().getConfigManager().getDiscordLink());
        return true;
    }
}
