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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ShopGUI {

    private static ItemStack backgroundPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.parse("<gray>"));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static long getPlayerCoinsSafe(Player player) {
        var gm = GameManager.getInstance();
        if (gm == null) return 0L;
        var session = gm.getPlayerSession(player);
        if (session == null) return 0L;
        var ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return 0L;
        return ccp.getCoins();
    }

    public static void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(null, GuiType.MAIN), 54, Messages.parse("<gold><bold>Shop</bold></gold>"));

        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        ItemStack weapons = new ItemStack(Material.IRON_SWORD);
        ItemMeta weaponsItemMeta = weapons.getItemMeta();
        if (weaponsItemMeta != null) {
            weaponsItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.WEAPONS.getDisplayName() + "</yellow>"));
            weaponsItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            weapons.setItemMeta(weaponsItemMeta);
            inv.setItem(10, weapons);
        }

        ItemStack armor = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta armorItemMeta = armor.getItemMeta();
        if (armorItemMeta != null) {
            armorItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.ARMOR.getDisplayName() + "</yellow>"));
            armorItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            armor.setItemMeta(armorItemMeta);
            inv.setItem(11, armor);
        }

        ItemStack food = new ItemStack(Material.COOKED_BEEF);
        ItemMeta foodItemMeta = food.getItemMeta();
        if (foodItemMeta != null) {
            foodItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.FOOD.getDisplayName() + "</yellow>"));
            foodItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            food.setItemMeta(foodItemMeta);
            inv.setItem(12, food);
        }

        ItemStack utility = new ItemStack(Material.COBWEB);
        ItemMeta utilityItemMeta = utility.getItemMeta();
        if (utilityItemMeta != null) {
            utilityItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.UTILITY.getDisplayName() + "</yellow>"));
            utilityItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            utility.setItemMeta(utilityItemMeta);
            inv.setItem(13, utility);
        }

        ItemStack customItems = new ItemStack(Material.NAME_TAG);
        ItemMeta customItemsItemMeta = customItems.getItemMeta();
        if (customItemsItemMeta != null) {
            customItemsItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.CUSTOM_ITEMS.getDisplayName() + "</yellow>"));
            customItemsItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            customItems.setItemMeta(customItemsItemMeta);
            inv.setItem(14, customItems);
        }

        ItemStack enchants = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta enchantsItemMeta = enchants.getItemMeta();
        if (enchantsItemMeta != null) {
            enchantsItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.ENCHANTS.getDisplayName() + "</yellow>"));
            enchantsItemMeta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
            enchants.setItemMeta(enchantsItemMeta);
            inv.setItem(15, enchants);
        }

        ItemStack mapModifiers = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta mapModifiersItemMeta = mapModifiers.getItemMeta();
        if (mapModifiersItemMeta != null) {
            mapModifiersItemMeta.displayName(Messages.parse("<yellow>Soon!</yellow>"));
            mapModifiers.setItemMeta(mapModifiersItemMeta);
            inv.setItem(16, mapModifiers);
        }

        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta bundleItemMeta = (BundleMeta) bundle.getItemMeta();
        if (bundleItemMeta != null) {
            bundleItemMeta.displayName(Messages.parse("<yellow>" + ShopCategory.INVESTMENTS + "</yellow>"));
            bundleItemMeta.lore(List.of(Messages.parse("<gray>Click to browse investments</gray>")));
            bundleItemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            bundle.setItemMeta(bundleItemMeta);
            inv.setItem(22, bundle);
        }

        ItemStack coins = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta coinsMeta = coins.getItemMeta();
        if (coinsMeta != null) {
            long coinsAmount = getPlayerCoinsSafe(player);
            coinsMeta.displayName(Messages.parse("<yellow>Your Balance: <gray>" + coinsAmount + "</gray></yellow>"));
            coins.setItemMeta(coinsMeta);
            inv.setItem(53, coins);
        }

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cm = cancel.getItemMeta();
        if (cm != null) {
            cm.displayName(Messages.parse("<red>Cancel</red>"));
            cancel.setItemMeta(cm);
            inv.setItem(45, cancel);
        }

        player.openInventory(inv);
    }

    public static void openCategoryItems(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(null, category), 54, Messages.parse("<gold><bold>Shop</bold></gold>"));

        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        int slot = 10;
        if (category == ShopCategory.ENCHANTS) {
            for (EnchantEntry ee : EnchantEntry.values()) {
                for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
                    long price = ee.getPriceForLevel(lvl);
                    if (price <= 0) continue;

                    if (slot >= 45) break;

                    ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
                    ItemMeta meta = it.getItemMeta();
                    if (meta == null) continue;

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
                int amount = Math.max(1, Math.min(si.getMaxStack(), 64));

                ItemStack it = new ItemStack(si.getMaterial(), amount);
                ItemMeta meta = it.getItemMeta();
                if (meta == null) continue;

                String name = si.name().replace('_', ' ');
                meta.displayName(Messages.parse("<yellow>" + name.substring(0, 1).toUpperCase() + name.substring(1) + "</yellow>"));
                meta.lore(Arrays.asList(
                        Messages.parse("<gray>Price: <gold>$" + si.getPrice() + "</gold></gray>"),
                        Messages.parse("<gray>Max stack: <white>" + si.getMaxStack() + "</white></gray>")
                ));
                // add description from ShopItem if present
                String desc = si.getDescription();
                if (!desc.isEmpty()) meta.lore(List.of(Messages.parse(desc)));

                it.setItemMeta(meta);
                inv.setItem(slot++, it);
            }
        }

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cm = cancel.getItemMeta();
        if (cm != null) {
            cm.displayName(Messages.parse("<red>Cancel</red>"));
            cancel.setItemMeta(cm);
            inv.setItem(45, cancel);
        }

        // Decorative yellow highlight slots like the screenshot (46..50)
        ItemStack yellow = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta ym = yellow.getItemMeta();
        if (ym != null) {
            ym.displayName(Messages.parse("<yellow>"));
            yellow.setItemMeta(ym);
            for (int i = 46; i <= 50; i++) {
                inv.setItem(i, yellow);
            }
        }

        ItemStack undo = new ItemStack(Material.ARROW);
        ItemMeta um = undo.getItemMeta();
        if (um != null) {
            um.displayName(Messages.parse("<yellow>Undo Purchase</yellow>"));
            um.lore(List.of(Messages.parse("<gray>Undo last purchase and receive a refund</gray>")));
            undo.setItemMeta(um);
            inv.setItem(49, undo);
        }

        long coins = getPlayerCoinsSafe(player);

        ItemStack coinDisplay = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta pd = coinDisplay.getItemMeta();
        if (pd != null) {
            pd.displayName(Messages.parse("<green>Coins: <gold>$" + coins + "</gold></green>"));
            coinDisplay.setItemMeta(pd);
            inv.setItem(53, coinDisplay);
        }

        player.openInventory(inv);
    }
}
