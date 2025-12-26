package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Debug command to show player state, cooldowns, and items.
 */
public class DebugCommand extends AbstractArgCommand {

    public DebugCommand() {
        super("debug", List.of("d"), "cashclash.admin.debug");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length < 1) {
            Messages.send(sender, "<red>Usage: /cashclash debug <player></red>");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Messages.send(sender, "<red>Player not found: " + args[0] + "</red>");
            return false;
        }

        UUID uuid = target.getUniqueId();
        Messages.send(sender, "<gold>=== Debug: " + target.getName() + " ===</gold>");

        // Game session info
        GameSession session = GameManager.getInstance().getPlayerSession(target);
        if (session == null) {
            Messages.send(sender, "<gray>Not in a game session</gray>");
        } else {
            Messages.send(sender, "<green>Session ID: " + session.getSessionId() + "</green>");
            Messages.send(sender, "<green>Arena: " + session.getArenaNumber() + "</green>");

            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                Messages.send(sender, "<yellow>Coins: $" + String.format("%,d", ccp.getCoins()) + "</yellow>");
                Messages.send(sender, "<yellow>Lives: " + ccp.getLives() + "</yellow>");
            }

            // Mythic ownership
            MythicItem mythic = MythicItemManager.getInstance().getPlayerMythic(session, uuid);
            if (mythic != null) {
                Messages.send(sender, "<light_purple>Mythic: " + mythic.getDisplayName() + "</light_purple>");
            } else {
                Messages.send(sender, "<gray>No mythic owned</gray>");
            }
        }

        // Invis cloak status
        if (CustomItemManager.getInstance().isInvisActive(uuid)) {
            Messages.send(sender, "<dark_purple>Invisibility: ACTIVE</dark_purple>");
        }

        Messages.send(sender, "<gold>===========================</gold>");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, String @NotNull [] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

