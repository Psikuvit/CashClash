package me.psikuvit.cashClash.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility for creating and sending components using MiniMessage with italics disabled.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Messages() {
        throw new AssertionError("Nope.");
    }

    /**
     * Parse a MiniMessage or legacy string into a Component and disable italics on the root.
     */
    public static Component parse(String miniMsg) {
        final Component comp;
        if (miniMsg != null && miniMsg.contains("§")) {
            comp = LEGACY.deserialize(miniMsg);
        } else {
            comp = MINI.deserialize(miniMsg == null ? "" : miniMsg);
        }
        return comp.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Send a MiniMessage/legacy string to a player (parses then sends).
     */
    public static void send(Player player, String miniMsg) {
        player.sendMessage(parse(miniMsg));
    }

    /**
     * Send a MiniMessage/legacy string to any CommandSender (player/console).
     */
    public static void send(CommandSender sender, String miniMsg) {
        sender.sendMessage(parse(miniMsg));
    }

    /**
     * Send an already-built component to a player, ensuring italics disabled on the root.
     */
    public static void send(Player player, Component component) {
        player.sendMessage(component.decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Send an already-built component to a CommandSender, ensuring italics disabled on the root.
     */
    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(component.decoration(TextDecoration.ITALIC, false));
    }
}
