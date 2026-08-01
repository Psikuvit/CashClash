package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Admin testing command to force shields on/off for a player for the rest of the game.
 * Usage: /cc shield <on|off> [player]
 */
public class ShieldCommand extends AbstractArgCommand {

    public ShieldCommand() {
        super("shield", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            Messages.send(sender, "shield.usage");
            return true;
        }

        boolean give;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> give = true;
            case "off" -> give = false;
            default -> {
                Messages.send(sender, "shield.usage");
                return true;
            }
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, "shield.player-not-found", "player", args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                Messages.send(sender, "shield.need-player-or-target");
                return true;
            }
            target = (Player) sender;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(target);
        if (session == null) {
            Messages.send(sender, "shield.target-not-in-game", "player_name", target.getName());
            return true;
        }

        session.setShieldOverride(target.getUniqueId(), give);
        Kit.setShield(target, give);

        String state = give ? "on" : "off";
        Messages.send(target, "shield.success", "state", state);
        if (!target.equals(sender)) {
            Messages.send(sender, "shield.success-other", "player_name", target.getName(), "state", state);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("on", "off")
                    .filter(s -> s.startsWith(token))
                    .toList();
        }

        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(token))
                    .toList();
        }

        return Collections.emptyList();
    }
}
