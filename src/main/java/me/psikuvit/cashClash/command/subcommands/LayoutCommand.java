package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gui.LayoutKitSelectorGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command for managing kit layouts.
 * Usage:
 * /cc layout - Opens the kit selector GUI
 * /cc layout confirm - Confirms and saves the current layout
 * /cc layout cancel - Cancels layout editing
 */
public class LayoutCommand extends AbstractArgCommand {

    public LayoutCommand() {
        super("layout", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        // Don't allow if in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) {
            Messages.send(player, "command.in-game");
            return true;
        }

        LayoutManager layoutManager = LayoutManager.getInstance();

        if (args.length == 0) {
            // Check if already editing
            if (layoutManager.isEditing(player)) {
                Messages.send(player, "lobby.layout-editing-instructions");
                Messages.send(player, "lobby.layout-editing-cancel-instructions");
                return true;
            }

            // Open kit selector GUI
            LayoutKitSelectorGUI.open(player);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "confirm", "save" -> {
                if (!layoutManager.isEditing(player)) {
                    Messages.send(player, "lobby.not-editing-layout");
                    return true;
                }
                layoutManager.confirmLayout(player);
            }
            case "cancel", "quit", "exit" -> {
                if (!layoutManager.isEditing(player)) {
                    Messages.send(player, "lobby.not-editing-layout");
                    return true;
                }
                layoutManager.cancelEditing(player);
            }
            default -> {
                Messages.send(player, "<red>Unknown action: " + action + "</red>");
                Messages.send(player, "<gray>Usage: /cc layout [confirm|cancel]</gray>");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Stream.of("confirm", "cancel")
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}

