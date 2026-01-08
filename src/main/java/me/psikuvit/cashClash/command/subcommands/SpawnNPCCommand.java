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
            Messages.send(sender, "<red>Only players can spawn NPCs.</red>");
            return true;
        }

        if (args.length == 0) {
            Messages.send(player, "<yellow>Usage: /cc spawnnpc <arena|remove></yellow>");
            Messages.send(player, "<gray>  arena - Spawn an arena selector NPC at your location</gray>");
            Messages.send(player, "<gray>  remove - Remove nearby arena NPCs (within 5 blocks)</gray>");
            Messages.send(player, "<gray>Total saved NPCs: " + MannequinManager.getInstance().getCount() + "</gray>");
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
                    Messages.send(player, "<green>Removed " + removed + " arena NPC(s) within 5 blocks.</green>");
                } else {
                    Messages.send(player, "<yellow>No arena NPCs found within 5 blocks.</yellow>");
                }
            }
            default -> Messages.send(player, "<red>Unknown NPC type. Use: arena, remove</red>");
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

