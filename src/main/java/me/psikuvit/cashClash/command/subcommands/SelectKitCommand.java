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
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length < 1) {
            Messages.send(sender, "selectkit.usage");
            Messages.send(sender, "selectkit.available-kits", "kits", getKitList());
            return true;
        }

        String kitName = args[0].toUpperCase(Locale.ROOT);
        Kit kit;
        try {
            kit = Kit.valueOf(kitName);
        } catch (IllegalArgumentException e) {
            Messages.send(sender, "selectkit.invalid-kit", "kit_name", args[0]);
            Messages.send(sender, "selectkit.available-kits", "kits", getKitList());
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, "selectkit.player-not-found", "player", args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Messages.send(sender, "selectkit.need-player-or-target");
                return true;
            }
            target = (Player) sender;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(target);
        if (session == null) {
            Messages.send(sender, "selectkit.target-not-in-game", "player_name", target.getName());
            return true;
        }

        GameState state = session.getState();
        if (state == GameState.COMBAT) {
            Messages.send(sender, "selectkit.warning");
        }

        CashClashPlayer ccp = session.getCashClashPlayer(target.getUniqueId());
        if (ccp == null) {
            Messages.send(sender, "selectkit.data-not-found", "player_name", target.getName());
            return true;
        }

        target.getInventory().clear();

        ccp.setCurrentKit(kit);

        PlayerData playerData = PlayerDataManager.getInstance().getData(target.getUniqueId());
        int currentRound = session.getCurrentRound();
        boolean rounds1to3HaveShields = session.hasShieldsInRounds1to3();

        if (playerData.hasKitLayout(kit.name())) {
            Map<Integer, String> layout = playerData.getKitLayout(kit.name());
            kit.applyWithLayout(target, layout, currentRound, rounds1to3HaveShields);
        } else {
            kit.apply(target, currentRound, rounds1to3HaveShields);
        }

        String display = kit.getDisplayName();
        Messages.send(target, "selectkit.success", "kit_name", display);

        if (!target.equals(sender)) {
            Messages.send(sender, "selectkit.success-other", "player_name", target.getName(), "kit_name", display);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(Kit.values())
                    .map(Kit::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
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
