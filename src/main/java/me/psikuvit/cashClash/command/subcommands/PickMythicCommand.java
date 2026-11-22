package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /cc pickmythic <mythic>}: the next game sells that mythic. Run it once per mythic to
 * pick them all; any slots left are filled at random. {@code /cc pickmythic} lists the picks,
 * {@code /cc pickmythic clear} drops them.
 */
public class PickMythicCommand extends AbstractArgCommand {

    private static final String CLEAR = "clear";

    public PickMythicCommand() {
        super("pickmythic", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        MythicItemManager mythicManager = CashClashPlugin.getInstance().getMythicItemManager();
        if (args.length < 1) {
            List<MythicItem> picked = mythicManager.getPickedMythics();
            if (picked.isEmpty()) {
                Messages.send(sender, "admin.pickmythic-usage");
            } else {
                Messages.send(sender, "admin.pickmythic-list", "mythics",
                        String.join(", ", picked.stream().map(MythicItem::getDisplayName).toList()));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase(CLEAR)) {
            mythicManager.clearPickedMythics();
            Messages.send(sender, "admin.pickmythic-cleared");
            return true;
        }

        MythicItem mythic = ShopItems.getMythic(args[0].toUpperCase(Locale.ROOT));
        if (mythic == null) {
            Messages.send(sender, "generic.mythic-not-found", "item_name", args[0]);
            return true;
        }

        boolean added = mythicManager.pickMythic(mythic);
        Messages.send(sender, added ? "admin.pickmythic-added" : "admin.pickmythic-already", "mythic", mythic.getDisplayName());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return List.of();

        String last = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (MythicItem mythic : MythicItem.values()) {
            if (mythic.name().toLowerCase(Locale.ROOT).startsWith(last)) out.add(mythic.name());
        }
        if (CLEAR.startsWith(last)) out.add(CLEAR);
        return out;
    }
}
