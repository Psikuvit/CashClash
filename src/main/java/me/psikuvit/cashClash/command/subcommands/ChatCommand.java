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
            Messages.send(sender, "<red>Only players can use chat commands.</red>");
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
                Messages.send(player, "<gray>Switched to </gray><white>Global</white><gray> chat.</gray>");
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
        Messages.send(player, "<gray>Current chat channel: </gray>" + channel.getNameColor() + channel.getDisplayName());
        Messages.send(player, "<gray>Use </gray><yellow>/cc chat <channel></yellow><gray> to switch.</gray>");
        Messages.send(player, "<gray>Quick prefixes: </gray><aqua>@p</aqua><gray>, </gray><green>@t</green><gray>, </gray><gold>@g</gold><gray>, </gray><white>@a</white>");
    }

    private void showHelp(Player player) {
        Messages.send(player, "<gold>═══════ Chat Commands ═══════</gold>");
        Messages.send(player, "<yellow>/cc chat global</yellow> <gray>- Switch to global chat</gray>");
        Messages.send(player, "<yellow>/cc chat party</yellow> <gray>- Toggle party chat</gray>");
        Messages.send(player, "<yellow>/cc chat team</yellow> <gray>- Toggle team chat (in-game)</gray>");
        Messages.send(player, "<yellow>/cc chat game</yellow> <gray>- Toggle game chat (in-game)</gray>");
        Messages.send(player, "");
        Messages.send(player, "<yellow>Quick prefixes in chat:</yellow>");
        Messages.send(player, "<aqua>@p <msg></aqua> <gray>- Send to party</gray>");
        Messages.send(player, "<green>@t <msg></green> <gray>- Send to team</gray>");
        Messages.send(player, "<gold>@g <msg></gold> <gray>- Send to game</gray>");
        Messages.send(player, "<white>@a <msg></white> <gray>- Send to all (global)</gray>");
        Messages.send(player, "<gold>══════════════════════════════</gold>");
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

