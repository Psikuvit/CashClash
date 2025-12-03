package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ShopGUI {

    private static ItemStack backgroundPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Messages.parse("<gray>"));
        pane.setItemMeta(meta);
        return pane;
    }

    public static void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(null, "categories"), 9, Messages.parse("<gold><bold>Shop</bold></gold>"));

        // Fill background with panes
        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < cats.length && i < 7; i++) {
            int slot = i + 1;

            ShopCategory c = cats[i];
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(Messages.parse("<yellow>" + c.getDisplayName() + "</yellow>"));
            meta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));

            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        // Cancel button at bottom-right of the small GUI (slot 8)
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cm = cancel.getItemMeta();

        cm.displayName(Messages.parse("<red>Cancel</red>"));
        cancel.setItemMeta(cm);

        inv.setItem(8, cancel);
        player.openInventory(inv);
    }

    public static void openCategoryItems(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(null, "category:" + category.getDisplayName()), 54, Messages.parse("<gold><bold>Shop</bold></gold>"));

        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        int slot = 10;
        if (category == ShopCategory.ENCHANTS) {
            for (EnchantEntry ee : EnchantEntry.values()) {
                for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
                    long price = ee.getPriceForLevel(lvl);
                    if (price <= 0) continue;

                    if (slot >= 45) break;

                    ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
                    ItemMeta meta = it.getItemMeta();

                    meta.displayName(Messages.parse("<yellow>" + ee.getDisplayName() + " " + lvl + "</yellow>"));
                    meta.lore(Arrays.asList(
                            Messages.parse("<gray>Price: <gold>$" + price + "</gold></gray>"),
                            Messages.parse("<gray>Max Level: <white>" + ee.getMaxLevel() + "</white></gray>"),
                            Messages.parse("<gray>Applies to: <white>" + ee.getApplicableMaterials().toString() + "</white></gray>")
                    ));

                    it.setItemMeta(meta);
                    inv.setItem(slot++, it);
                }
            }
        } else {
            List<ShopItem> items = Arrays.stream(ShopItem.values())
                    .filter(i -> i.getCategory() == category)
                    .toList();

            for (ShopItem si : items) {
                if (slot >= 45) break;
                ItemStack it = new ItemStack(si.getMaterial(), Math.max(1, Math.min(si.getMaxStack(), 64)));
                ItemMeta meta = it.getItemMeta();

                meta.displayName(Messages.parse("<yellow>" + si.name().replace('_',' ') + "</yellow>"));
                meta.lore(Arrays.asList(
                        Messages.parse("<gray>Price: <gold>$" + si.getPrice() + "</gold></gray>"),
                        Messages.parse("<gray>Max stack: <white>" + si.getMaxStack() + "</white></gray>")
                ));

                it.setItemMeta(meta);
                inv.setItem(slot++, it);
            }
        }

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cm = cancel.getItemMeta();

        cm.displayName(Messages.parse("<red>Cancel</red>"));
        cancel.setItemMeta(cm);
        inv.setItem(45, cancel);

        // Decorative yellow highlight slots like the screenshot (46..50)
        ItemStack yellow = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta ym = yellow.getItemMeta();

        ym.displayName(Messages.parse("<yellow>"));
        yellow.setItemMeta(ym);

        for (int i = 46; i <= 50; i++) inv.setItem(i, yellow);

        ItemStack undo = new ItemStack(Material.ARROW);
        ItemMeta um = undo.getItemMeta();

        um.displayName(Messages.parse("<yellow>Undo Purchase</yellow>"));
        um.lore(List.of(Messages.parse("<gray>Undo last purchase and receive a refund</gray>")));
        undo.setItemMeta(um);

        inv.setItem(49, undo);

        long coins = 0;
        var session = GameManager.getInstance().getPlayerSession(player);

        if (session != null) {
            var ccp = session.getCashClashPlayer(player.getUniqueId());
            if (ccp != null) coins = ccp.getCoins();
        }

        ItemStack coinDisplay = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta pd = coinDisplay.getItemMeta();

        pd.displayName(Messages.parse("<green>Coins: <gold>$" + coins + "</gold></green>"));
        coinDisplay.setItemMeta(pd);

        inv.setItem(53, coinDisplay);
        player.openInventory(inv);
    }
}
