package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.game.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Utility for creating and sending components using MiniMessage with italics disabled.
 * Centralized message handling for the Cash Clash plugin.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int DEFAULT_WRAP_WIDTH = 40;
    private static final MessagesConfig config = MessagesConfig.getInstance();


    /**
     * Log a debug message to the console if debug mode is enabled.
     *
     * @param category The debug category (type-safe enum)
     * @param message The debug message
     */
    public static void debug(@NotNull DebugCategory category, @NotNull String message) {
        if (!isDebugEnabled()) return;
        CashClashPlugin.getInstance().getLogger().info("[DEBUG:" + category.name() + "] " + message);
    }

    private Messages() {
        throw new AssertionError("Nope.");
    }

    // ==================== DEBUG ====================

    /**
     * Check if debug mode is enabled in config.
     */
    public static boolean isDebugEnabled() {
        return ConfigManager.getInstance().isDebugEnabled();
    }

    /**
     * Log a debug message to the console if debug mode is enabled.
     *
     * @param category The debug category (string for backwards compatibility)
     * @param message The debug message
     */
    public static void debug(@NotNull String category, @NotNull String message) {
        if (!isDebugEnabled()) return;
        CashClashPlugin.getInstance().getLogger().info("[DEBUG:" + category + "] " + message);
    }

    /**
     * Log a debug message to the console if debug mode is enabled.
     * Simple format without category.
     *
     * @param message The debug message
     */
    public static void debug(@NotNull String message) {
        if (!isDebugEnabled()) return;
        CashClashPlugin.getInstance().getLogger().info("[DEBUG] " + message);
    }

    /**
     * Log a debug message with player context to the console if debug mode is enabled.
     *
     * @param player The player related to this debug message
     * @param category The debug category (type-safe enum)
     * @param message The debug message
     */
    public static void debug(@Nullable Player player, @NotNull DebugCategory category, @NotNull String message) {
        if (!isDebugEnabled()) return;
        String playerName = player != null ? player.getName() : "null";
        CashClashPlugin.getInstance().getLogger().info("[DEBUG:" + category.name() + "] [" + playerName + "] " + message);
    }

    /**
     * Log a debug message with player context to the console if debug mode is enabled.
     *
     * @param player The player related to this debug message
     * @param category The debug category
     * @param message The debug message
     */
    public static void debug(@Nullable Player player, @NotNull String category, @NotNull String message) {
        if (!isDebugEnabled()) return;
        String playerName = player != null ? player.getName() : "null";
        CashClashPlugin.getInstance().getLogger().info("[DEBUG:" + category + "] [" + playerName + "] " + message);
    }

    /**
     * Log a debug message with player context to the console if debug mode is enabled.
     * Simple format without category.
     *
     * @param player The player related to this debug message
     * @param message The debug message
     */
    public static void debug(@Nullable Player player, @NotNull String message) {
        if (!isDebugEnabled()) return;
        String playerName = player != null ? player.getName() : "null";
        CashClashPlugin.getInstance().getLogger().info("[DEBUG] [" + playerName + "] " + message);
    }

    /**
     * Parse a MiniMessage or legacy string into a Component and disable italics on the root.
     *
     * @param miniMsg The message to parse (can be null or empty)
     * @return The parsed component, never null
     */
    @NotNull
    public static Component parse(@Nullable String miniMsg) {
        if (miniMsg == null || miniMsg.isEmpty()) {
            return Component.empty();
        }
        final Component comp = miniMsg.contains("§")
                ? LEGACY.deserialize(miniMsg)
                : MINI.deserialize(miniMsg);
        return comp.decoration(TextDecoration.ITALIC, false);
    }

    // ==================== PARSING ====================

    /**
     * Serialize a Component to legacy format string.
     *
     * @param comp The component to serialize (can be null)
     * @return The legacy string, empty string if comp is null
     */
    @NotNull
    public static String parseToLegacy(@Nullable Component comp) {
        return comp == null ? "" : LEGACY.serialize(comp);
    }

    /**
     * Send a MiniMessage/legacy string to a player (parses then sends).
     *
     * @param player The player to send to (can be null)
     * @param key The message to send
     */
    public static void send(@Nullable Player player, @Nullable String key) {
        if (player != null && player.isOnline()) {
            player.sendMessage(parse(config.getRaw(key)));
        }
    }

    /**
     * Send a MiniMessage/legacy string to a player (parses then sends).
     *
     * @param player The player to send to (can be null)
     * @param key The message to send
     */
    public static void send(@Nullable Player player, @Nullable String key, @NotNull Object... args) {
        if (player != null && player.isOnline()) {
            player.sendMessage(parse(config.getMessage(key, args)));
        }
    }


    /**
     * Send a message to a CommandSender using a message key.
     * For console/non-player senders, use the Component-based send() method instead.
     *
     * @param sender The sender to send to (can be null)
     * @param key The message key from MessagesConfig
     */
    public static void send(@Nullable CommandSender sender, @Nullable String key) {
        if (sender != null) {
            sender.sendMessage(parse(config.getRaw(key)));
        }
    }

    /**
     * Send a message to a CommandSender using a message key.
     * For console/non-player senders, use the Component-based send() method instead.
     *
     * @param sender The sender to send to (can be null)
     * @param key The message key from MessagesConfig
     */
    public static void send(@Nullable CommandSender sender, @Nullable String key, @NotNull Object... args) {
        if (sender != null) {
            sender.sendMessage(parse(config.getMessage(key, args)));
        }
    }

    /**
     * Wrap a (mostly single-tagged) mini-message into multiple component lines suitable for item lore.
     * This handles simple cases like "<gray>Long description here...</gray>" by splitting words
     * and re-applying the same outer tag per resulting line. If no outer tag is present the method
     * will still wrap plain text.
     *
     * @param miniMsg the input mini-message / legacy string
     * @return list of Components (one per lore line), never null
     */
    @NotNull
    public static List<Component> wrapLines(@Nullable String miniMsg) {
        return wrapLines(miniMsg, DEFAULT_WRAP_WIDTH);
    }

    /**
     * Broadcast a message to a collection of player UUIDs.
     */
    public static void broadcast(Collection<UUID> players, String key) {
        players.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                send(player, key);
            }
        });
    }

    /**
     * Broadcast a message to all players in a team.
     */
    public static void broadcastToTeam(Team team, String key) {
        if (team != null) {
            broadcast(team.getPlayers(), key);
        }
    }

    /**
     * Wrap a mini-message into multiple component lines with custom width.
     *
     * @param miniMsg the input mini-message / legacy string
     * @param maxChars maximum characters per line (uses default if <= 0)
     * @return list of Components (one per lore line), never null
     */
    @NotNull
    public static List<Component> wrapLines(@Nullable String miniMsg, int maxChars) {
        if (miniMsg == null || miniMsg.isBlank()) {
            return Collections.emptyList();
        }

        // Use default if invalid
        if (maxChars <= 0) {
            maxChars = DEFAULT_WRAP_WIDTH;
        }

        String trimmed = miniMsg.trim();
        String prefixTag = null;
        String suffixTag = null;
        String inner = trimmed;

        if (trimmed.startsWith("<") && trimmed.contains(">")) {
            int idx = trimmed.indexOf('>');
            prefixTag = trimmed.substring(0, idx + 1);

            String tagName = prefixTag.substring(1, prefixTag.length() - 1).split(" ")[0];
            String possibleClosing = "</" + tagName + ">";

            if (trimmed.endsWith(possibleClosing)) {
                suffixTag = possibleClosing;
                inner = trimmed.substring(idx + 1, trimmed.length() - possibleClosing.length()).trim();
            } else {
                inner = trimmed.substring(idx + 1).trim();
            }
        }

        if (inner.isEmpty()) {
            return List.of(parse(trimmed));
        }

        String[] words = inner.split("\\s+");
        List<Component> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (String w : words) {
            if (sb.isEmpty()) {
                sb.append(w);
            } else if (sb.length() + 1 + w.length() <= maxChars) {
                sb.append(' ').append(w);
            } else {
                String line = sb.toString();
                String toParse = (prefixTag != null ? prefixTag : "") + line + (suffixTag != null ? suffixTag : "");
                lines.add(parse(toParse));

                sb.setLength(0);
                sb.append(w);
            }
        }

        if (!sb.isEmpty()) {
            String line = sb.toString();
            String toParse = (prefixTag != null ? prefixTag : "") + line + (suffixTag != null ? suffixTag : "");
            lines.add(parse(toParse));
        }

        return lines;
    }

    /**
     * Debug categories for type-safe debug logging.
     */
    public enum DebugCategory {
        MYTHIC, SHOP, GAME, ARENA, PLAYER, CONFIG, PARTY, ECONOMY, KIT, LOBBY, LORE
    }
}
