package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JoinCommand extends AbstractArgCommand {
    public JoinCommand() {
        super("join", List.of(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }
        ArenaSelectionGUI.openArenaGUI(player);
        Messages.send(player, "<yellow>Select an arena to join!</yellow>");
        return true;
    }
}

