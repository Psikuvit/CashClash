package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.lobby.PracticeRangeManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /cc practice - enters the practice range. Admins set it up with
 * /cc practice setspawn | setvillager | addskeleton | zombiestart | zombieend | clear,
 * each using where they're standing.
 */
public class PracticeCommand extends AbstractArgCommand {

    private static final List<String> SETUP = List.of("setspawn", "setvillager", "addskeleton", "zombiestart", "zombieend", "clear");

    public PracticeCommand() {
        super("practice", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        PracticeRangeManager practice = CashClashPlugin.getInstance().getPracticeRangeManager();
        if (args.length == 0) {
            practice.enter(player);
            return true;
        }

        if (!player.hasPermission("cashclash.admin")) {
            Messages.send(player, "practice.usage");
            return true;
        }

        Location here = LocationUtils.clone(player.getLocation());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                practice.setSpawn(here);
                Messages.send(player, "practice.spawn-set");
            }
            case "setvillager" -> {
                practice.setVillagerLocation(here);
                Messages.send(player, "practice.villager-set");
            }
            case "addskeleton" -> Messages.send(player, "practice.skeleton-added",
                    "count", String.valueOf(practice.addSkeleton(here)));
            case "zombiestart" -> {
                practice.setPendingZombieStart(player, here);
                Messages.send(player, "practice.zombie-start-set");
            }
            case "zombieend" -> {
                int count = practice.addZombiePath(player, here);
                if (count < 0) {
                    Messages.send(player, "practice.zombie-no-start");
                } else {
                    Messages.send(player, "practice.zombie-added", "count", String.valueOf(count));
                }
            }
            case "clear" -> {
                practice.clearMobs();
                Messages.send(player, "practice.cleared");
            }
            default -> Messages.send(player, "practice.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission("cashclash.admin")) return Collections.emptyList();
        String last = args[0].toLowerCase(Locale.ROOT);
        return SETUP.stream().filter(option -> option.startsWith(last)).toList();
    }
}
