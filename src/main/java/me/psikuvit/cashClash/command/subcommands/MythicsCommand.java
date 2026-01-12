package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MythicsCommand extends AbstractArgCommand {

    public MythicsCommand() {
        super("mythic", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NonNull @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            return true;
        }

        MythicItem mythicItem = ShopItems.getMythic(args[0]);

        if (mythicItem == null) {
            Messages.send(player, "<red>Mythic item not found: " + args[0] + "</red>");
            return true;
        }

        // BlazeBite gives two crossbows
        if (mythicItem == MythicItem.BLAZEBITE_CROSSBOWS) {
            ItemStack[] crossbows = MythicItemManager.getInstance().createBlazebiteBundle(player);
            player.getInventory().addItem(crossbows[0]); // Glacier
            player.getInventory().addItem(crossbows[1]); // Volcano
        } else {
            ItemStack item = MythicItemManager.getInstance().createMythicItem(mythicItem, player);
            player.getInventory().addItem(item);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) return Collections.emptyList();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            for (MythicItem a : MythicItem.values())
                if (a.name().startsWith(last)) out.add(a.name());
            return out;
        }
        return Collections.emptyList();
    }
}
