package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.party.PartyManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standalone party command handler.
 * Usage: /party <create|invite|accept|deny|leave|kick|transfer|disband|info|chat|list>
 */
public class PartyCommandHandler extends Command {

    public PartyCommandHandler() {
        super("party");
        setDescription("Party management commands");
        setUsage("/<command> <create|invite|accept|deny|leave|kick|transfer|disband|info|chat>");
        setAliases(List.of("p"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "party.only-players");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        PartyManager pm = PartyManager.getInstance();

        switch (subCommand) {
            case "create" -> pm.createParty(player);

            case "invite" -> {
                if (args.length < 2) {
                    Messages.send(player, "party.invite-usage");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "party.player-not-found");
                    return true;
                }
                pm.invitePlayer(player, target);
            }

            case "accept" -> {
                String inviterName = args.length > 1 ? args[1] : "";
                pm.acceptInvite(player, inviterName);
            }

            case "deny", "decline" -> {
                String inviterName = args.length > 1 ? args[1] : "";
                pm.denyInvite(player, inviterName);
            }

            case "leave" -> pm.leaveParty(player);

            case "kick" -> {
                if (args.length < 2) {
                    Messages.send(player, "party.kick-usage");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "party.player-not-found");
                    return true;
                }
                pm.kickPlayer(player, target);
            }

            case "transfer", "promote" -> {
                if (args.length < 2) {
                    Messages.send(player, "party.transfer-usage");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "party.player-not-found");
                    return true;
                }
                pm.transferOwnership(player, target);
            }

            case "disband" -> {
                var party = pm.getPlayerParty(player);
                if (party != null) {
                    pm.disbandParty(party, player);
                } else {
                    Messages.send(player, "party.not-in-party");
                }
            }

            case "info", "list", "members" -> pm.showPartyInfo(player);

            case "chat", "c" -> {
                if (args.length < 2) {
                    Messages.send(player, "party.chat-usage");
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                pm.sendPartyMessage(player, message);
            }

            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        Messages.send(player, "party.help-title");
        Messages.send(player, "party.help-create");
        Messages.send(player, "party.help-invite");
        Messages.send(player, "party.help-accept");
        Messages.send(player, "party.help-deny");
        Messages.send(player, "party.help-leave");
        Messages.send(player, "party.help-kick");
        Messages.send(player, "party.help-transfer");
        Messages.send(player, "party.help-disband");
        Messages.send(player, "party.help-info");
        Messages.send(player, "party.help-chat");
        Messages.send(player, "party.help-footer");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull @NonNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String cmd : List.of("create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "info", "chat")) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (List.of("invite", "kick", "transfer", "accept", "deny").contains(subCmd)) {
                String input = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}

