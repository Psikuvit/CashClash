package me.psikuvit.cashClash.util.command;

import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Common command validation helpers to eliminate duplicate Player checks
 * across command subcommand classes.
 */
public class CommandValidation {
    private CommandValidation() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Validate that the command sender is a Player.
     * Sends error message and returns false if validation fails.
     */
    public static boolean validatePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.send(sender, "command.only-players");
            return false;
        }
        return true;
    }

    /**
     * Get the Player from CommandSender, or null if not a Player.
     * Sends error message if validation fails.
     */
    public static Player getPlayerOrNull(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return null;
        }
        return player;
    }
}
