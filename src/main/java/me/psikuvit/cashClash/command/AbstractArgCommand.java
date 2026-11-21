package me.psikuvit.cashClash.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class AbstractArgCommand {

    private final String name;
    private final List<String> aliases;
    private final String permission;

    protected AbstractArgCommand(String name, List<String> aliases, String permission) {
        this.name = name;
        this.aliases = aliases == null ? Collections.emptyList() : aliases;
        this.permission = permission;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getPermission() {
        return permission;
    }

    /**
     * A further check on who may run this command, on top of its permission. Everyone, unless a
     * command narrows it.
     */
    public boolean isAllowed(@NotNull CommandSender sender) {
        return true;
    }

    /**
     * Execute the command. args does NOT include the subcommand token.
     */
    public abstract boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args);

    /**
     * Tab completion for this subcommand. args does NOT include the subcommand token.
     */
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

