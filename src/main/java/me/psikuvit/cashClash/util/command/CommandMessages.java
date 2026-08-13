package me.psikuvit.cashClash.util.command;

import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Common command message patterns to provide consistent user feedback
 * and eliminate repeated Messages.send() calls across command classes.
 */
public class CommandMessages {
    private CommandMessages() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static void sendPlayerOnly(CommandSender sender) {
        Messages.send(sender, "command.only-players");
    }

    public static void sendMustBeInGame(Player player) {
        Messages.send(player, "command.must-be-in-game");
    }

    public static void sendInvalidArguments(Player player) {
        Messages.send(player, "command.invalid-arguments");
    }

    public static void sendInvalidGamemode(Player player) {
        Messages.send(player, "command.invalid-gamemode");
    }

    public static void sendNotPermitted(CommandSender sender) {
        Messages.send(sender, "command.no-permission");
    }
}
