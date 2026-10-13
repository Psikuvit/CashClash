package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /cc customitem &lt;item&gt; [amount] - gives the sender a custom item, in a game or in the hub.
 */
public class CustomItemCommand extends AbstractArgCommand {

    public CustomItemCommand() {
        super("customitem", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        if (args.length == 0) {
            Messages.send(player, "generic.customitem-usage");
            return true;
        }

        CustomItem customItem = ShopItems.getCustomItem(args[0].toUpperCase(Locale.ROOT));
        if (customItem == null) {
            Messages.send(player, "generic.customitem-not-found", "item_name", args[0]);
            return true;
        }

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                Messages.send(player, "generic.customitem-usage");
                return true;
            }
        }

        ItemStack item = CashClashPlugin.getInstance().getItemFactory().createCustomItem(customItem, player);
        item.setAmount(Math.min(amount, item.getMaxStackSize()));
        player.getInventory().addItem(item);
        Messages.send(player, "generic.customitem-given",
                "item_name", customItem.getDisplayName(),
                "amount", String.valueOf(item.getAmount()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String last = args[0].toLowerCase(Locale.ROOT);
        return Arrays.stream(CustomItem.values())
                .map(item -> item.name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(last))
                .toList();
    }
}
