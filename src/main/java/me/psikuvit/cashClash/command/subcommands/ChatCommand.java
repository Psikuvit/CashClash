package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.chat.ChatChannel;
import me.psikuvit.cashClash.chat.ChatManager;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat channel management command.
 * Usage: /cc chat <channel|message>
 */
public class ChatCommand extends AbstractArgCommand {

    public ChatCommand() {
        super("chat", List.of("c", "channel"), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        if (args.length == 0) {
            showCurrentChannel(player);
            return true;
        }

        String arg = args[0].toLowerCase();

        // Check if switching channels
        switch (arg) {
            case "global", "g", "all", "a" -> {
                ChatManager.getInstance().setPlayerChannel(player, ChatChannel.GLOBAL);
                Messages.send(player, "chat.switched-to-global");
            }
            case "party", "p" -> ChatManager.getInstance().toggleChannel(player, ChatChannel.PARTY);
            case "team", "t" -> ChatManager.getInstance().toggleChannel(player, ChatChannel.TEAM);
            case "game" -> ChatManager.getInstance().toggleChannel(player, ChatChannel.GAME);
            case "help" -> showHelp(player);
            default -> {
                // Treat as a message - try to send based on current channel
                String message = String.join(" ", args);
                if (!ChatManager.getInstance().processMessage(player, message)) {
                    // Global message - just show help since we can't send it without async chat event
                    showHelp(player);
                }
            }
        }

        return true;
    }

    private void showCurrentChannel(Player player) {
        ChatChannel channel = ChatManager.getInstance().getPlayerChannel(player);
        Messages.send(player, "chat.current-channel",
                "channel_color", channel.getNameColor(),
                "channel_name", channel.getDisplayName());
        Messages.send(player, "chat.switch-hint");
        Messages.send(player, "chat.quick-prefixes");
    }

    private void showHelp(Player player) {
        Messages.send(player, "chat.help-title");
        Messages.send(player, "chat.help-global");
        Messages.send(player, "chat.help-party");
        Messages.send(player, "chat.help-team");
        Messages.send(player, "chat.help-game");
        Messages.send(player, "chat.help-spacer");
        Messages.send(player, "chat.help-quick-title");
        Messages.send(player, "chat.help-quick-party");
        Messages.send(player, "chat.help-quick-team");
        Messages.send(player, "chat.help-quick-game");
        Messages.send(player, "chat.help-quick-global");
        Messages.send(player, "chat.help-footer");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String channel : List.of("global", "party", "team", "game", "help")) {
                if (channel.startsWith(input)) {
                    completions.add(channel);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}

