package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Utility for creating and sending components using MiniMessage with italics disabled.
 * Centralized message handling for the Cash Clash plugin.
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
        if (player != null && player.isOnline()) {
            player.sendMessage(parse(miniMsg));
        }
    }

    /**
     * Send a MiniMessage/legacy string to any CommandSender (player/console).
     */
    public static void send(CommandSender sender, String miniMsg) {
        if (sender != null) {
            sender.sendMessage(parse(miniMsg));
        }
    }

    /**
     * Send an already-built component to a player, ensuring italics disabled on the root.
     */
    public static void send(Player player, Component component) {
        if (player != null && player.isOnline()) {
            player.sendMessage(component.decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * Send an already-built component to a CommandSender, ensuring italics disabled on the root.
     */
    public static void send(CommandSender sender, Component component) {
        if (sender != null) {
            sender.sendMessage(component.decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * Broadcast a message to a collection of player UUIDs.
     */
    public static void broadcast(Collection<UUID> players, String miniMsg) {
        Component comp = parse(miniMsg);
        players.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                send(player, comp);
            }
        });
    }

    /**
     * Broadcast a component to a collection of player UUIDs.
     */
    public static void broadcast(Collection<UUID> players, Component component) {
        Component comp = component.decoration(TextDecoration.ITALIC, false);
        players.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                send(player, comp);
            }
        });
    }

    /**
     * Broadcast a message to all players in a team.
     */
    public static void broadcastToTeam(Team team, String miniMsg) {
        if (team != null) {
            broadcast(team.getPlayers(), miniMsg);
        }
    }

    /**
     * Broadcast a component to all players in a team.
     */
    public static void broadcastToTeam(Team team, Component component) {
        if (team != null) {
            broadcast(team.getPlayers(), component);
        }
    }

    /**
     * Broadcast a message with a prefix to a collection of player UUIDs.
     */
    public static void broadcastWithPrefix(Collection<UUID> players, String miniMsg) {
        String full = ConfigManager.getInstance().getPrefix() + " " + miniMsg;
        broadcast(players, full);
    }
}

