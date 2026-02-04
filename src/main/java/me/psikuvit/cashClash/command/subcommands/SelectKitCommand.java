package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin command to set or change a player's kit.
 * Usage: /cc selectkit <kit> [player]
 * If no player is specified, applies to the command sender.
 */
public class SelectKitCommand extends AbstractArgCommand {

    public SelectKitCommand() {
        super("selectkit", List.of("setkit", "kit"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            Messages.send(sender, "<red>Usage: /cc selectkit <kit> [player]</red>");
            Messages.send(sender, "<gray>Available kits: " + getKitList() + "</gray>");
            return true;
        }

        String kitName = args[0].toUpperCase(Locale.ROOT);
        Kit kit;
        try {
            kit = Kit.valueOf(kitName);
        } catch (IllegalArgumentException e) {
            Messages.send(sender, "<red>Invalid kit: " + args[0] + "</red>");
            Messages.send(sender, "<gray>Available kits: " + getKitList() + "</gray>");
            return true;
        }

        // Determine target player
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, "<red>Player not found: " + args[1] + "</red>");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Messages.send(sender, "<red>Console must specify a player: /cc selectkit <kit> <player></red>");
                return true;
            }
            target = (Player) sender;
        }

        // Check if target is in a game
        GameSession session = GameManager.getInstance().getPlayerSession(target);
        if (session == null) {
            Messages.send(sender, "<red>" + target.getName() + " is not in a game session.</red>");
            return true;
        }

        // Check game state - can only change kit during shopping or waiting
        GameState state = session.getState();
        if (state == GameState.COMBAT) {
            Messages.send(sender, "<yellow>Warning: Changing kit during combat phase!</yellow>");
        }

        CashClashPlayer ccp = session.getCashClashPlayer(target.getUniqueId());
        if (ccp == null) {
            Messages.send(sender, "<red>Could not find player data for " + target.getName() + ".</red>");
            return true;
        }

        // Clear current inventory and apply new kit
        target.getInventory().clear();

        // Update the player's current kit
        ccp.setCurrentKit(kit);

        // Check for custom layout
        PlayerData playerData = PlayerDataManager.getInstance().getData(target.getUniqueId());
        int currentRound = session.getCurrentRound();
        boolean rounds1to3HaveShields = session.hasShieldsInRounds1to3();

        if (playerData.hasKitLayout(kit.name())) {
            Map<Integer, String> layout = playerData.getKitLayout(kit.name());
            kit.applyWithLayout(target, layout, currentRound, rounds1to3HaveShields);
        } else {
            kit.apply(target, currentRound, rounds1to3HaveShields);
        }

        Messages.send(target, "<green>Your kit has been changed to: <yellow>" + kit.getDisplayName() + "</yellow></green>");

        if (!target.equals(sender)) {
            Messages.send(sender, "<green>Set " + target.getName() + "'s kit to: <yellow>" + kit.getDisplayName() + "</yellow></green>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Tab complete kit names
            String input = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(Kit.values())
                    .map(Kit::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Tab complete player names
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String getKitList() {
        return Arrays.stream(Kit.values())
                .map(Kit::name)
                .collect(Collectors.joining(", "));
    }
}
