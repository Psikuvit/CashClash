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
            Messages.send(sender, "<red>Only players can use party commands.</red>");
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
                    Messages.send(player, "<red>Usage: /party invite <player></red>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "<red>Player not found!</red>");
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
                    Messages.send(player, "<red>Usage: /party kick <player></red>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "<red>Player not found!</red>");
                    return true;
                }
                pm.kickPlayer(player, target);
            }

            case "transfer", "promote" -> {
                if (args.length < 2) {
                    Messages.send(player, "<red>Usage: /party transfer <player></red>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Messages.send(player, "<red>Player not found!</red>");
                    return true;
                }
                pm.transferOwnership(player, target);
            }

            case "disband" -> {
                var party = pm.getPlayerParty(player);
                if (party != null) {
                    pm.disbandParty(party, player);
                } else {
                    Messages.send(player, "<red>You are not in a party!</red>");
                }
            }

            case "info", "list", "members" -> pm.showPartyInfo(player);

            case "chat", "c" -> {
                if (args.length < 2) {
                    Messages.send(player, "<red>Usage: /party chat <message></red>");
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
        Messages.send(player, "<gold>═══════ Party Commands ═══════</gold>");
        Messages.send(player, "<yellow>/party create</yellow> <gray>- Create a new party</gray>");
        Messages.send(player, "<yellow>/party invite <player></yellow> <gray>- Invite a player</gray>");
        Messages.send(player, "<yellow>/party accept [player]</yellow> <gray>- Accept an invite</gray>");
        Messages.send(player, "<yellow>/party deny [player]</yellow> <gray>- Deny an invite</gray>");
        Messages.send(player, "<yellow>/party leave</yellow> <gray>- Leave your party</gray>");
        Messages.send(player, "<yellow>/party kick <player></yellow> <gray>- Kick a player (owner)</gray>");
        Messages.send(player, "<yellow>/party transfer <player></yellow> <gray>- Transfer ownership</gray>");
        Messages.send(player, "<yellow>/party disband</yellow> <gray>- Disband the party (owner)</gray>");
        Messages.send(player, "<yellow>/party info</yellow> <gray>- Show party info</gray>");
        Messages.send(player, "<yellow>/party chat <msg></yellow> <gray>- Send party message</gray>");
        Messages.send(player, "<gold>══════════════════════════════</gold>");
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

