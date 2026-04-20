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
            Messages.send(sender, "debug.usage");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Messages.send(sender, "generic.player-not-found");
            return false;
        }

        UUID uuid = target.getUniqueId();
        if (sender instanceof Player player) {
            Messages.send(player, "debug.session-header", "{player_name}", target.getName());

            // Game session info
            GameSession session = GameManager.getInstance().getPlayerSession(target);
            if (session == null) {
                Messages.send(player, "<gray>Not in a game session</gray>");
            } else {
                Messages.send(player, "debug.session-id", "{session_id}", session.getSessionId());
                Messages.send(player, "debug.arena", "{arena_number}", String.valueOf(session.getArenaNumber()));

                CashClashPlayer ccp = session.getCashClashPlayer(uuid);
                if (ccp != null) {
                    Messages.send(player, "stats.coins", "{coins}", String.format("%,d", ccp.getCoins()));
                    Messages.send(player, "debug.lives", "{lives}", String.valueOf(ccp.getLives()));
                }

                // Mythic ownership
                MythicItem mythic = MythicItemManager.getInstance().getPlayerMythic(session, uuid);
                if (mythic != null) {
                    Messages.send(player, "stats.mythic-owned", "{mythic_name}", mythic.getDisplayName());
                } else {
                    Messages.send(player, "stats.no-mythic");
                }
            }

            // Invis cloak status
            if (CustomItemManager.getInstance().isInvisActive(uuid)) {
                Messages.send(player, "<dark_purple>Invisibility: ACTIVE</dark_purple>");
            }

            Messages.send(player, "<gold>===========================</gold>");
        }
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

