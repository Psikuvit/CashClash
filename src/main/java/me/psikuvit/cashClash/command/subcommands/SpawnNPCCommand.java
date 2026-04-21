package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.lobby.MannequinManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Command to spawn an Arena NPC mannequin that opens the arena selection GUI when clicked.
 * Usage: /cc spawnnpc [arena|remove]
 */
public class SpawnNPCCommand extends AbstractArgCommand {

    public SpawnNPCCommand() {
        super("spawnnpc", List.of("npc"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        if (args.length == 0) {
            Messages.send(player, "spawnnpc.usage");
            Messages.send(player, "spawnnpc.help-arena");
            Messages.send(player, "spawnnpc.help-remove");
            Messages.send(player, "spawnnpc.total-saved", "count", String.valueOf(MannequinManager.getInstance().getCount()));
            return true;
        }

        String subAction = args[0].toLowerCase();

        switch (subAction) {
            case "arena" -> {
                MannequinManager.getInstance().createArenaMannequin(player.getLocation(), player);
            }
            case "remove" -> {
                int removed = MannequinManager.getInstance().removeNearby(player.getLocation(), 5);
                if (removed > 0) {
                    Messages.send(player, "spawnnpc.removed", "count", String.valueOf(removed));
                } else {
                    Messages.send(player, "spawnnpc.not-found");
                }
            }
            default -> Messages.send(player, "spawnnpc.invalid-type");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("arena", "remove");
        }
        return Collections.emptyList();
    }
}

