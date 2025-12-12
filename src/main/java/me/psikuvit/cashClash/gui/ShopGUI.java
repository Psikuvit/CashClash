package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopGUI {

    private static ItemStack backgroundPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        pane.setItemMeta(meta);
        return pane;
    }

    public static void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(
                new ShopHolder(null, GuiType.MAIN),
                54,
                Messages.parse("<gold><bold>Shop</bold></gold>")
        );

        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        // Category items - Row 2
        inv.setItem(20, createCategoryIcon(Material.IRON_AXE, ShopCategory.WEAPONS));
        inv.setItem(21, createCategoryIcon(Material.IRON_CHESTPLATE, ShopCategory.ARMOR));
        inv.setItem(22, createCategoryIcon(Material.GOLDEN_APPLE, ShopCategory.FOOD));
        inv.setItem(23, createCategoryIcon(Material.ENDER_PEARL, ShopCategory.UTILITY));
        inv.setItem(24, createCategoryIcon(Material.NAME_TAG, ShopCategory.CUSTOM_ITEMS));

        // Row 3
        inv.setItem(30, createCategoryIcon(Material.ENCHANTING_TABLE, ShopCategory.ENCHANTS));

        ItemStack coming = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta comingMeta = coming.getItemMeta();
        comingMeta.displayName(Messages.parse("<gray>Coming Soon!</gray>"));
        coming.setItemMeta(comingMeta);
        inv.setItem(31, coming);

        ItemStack bundle = new ItemStack(Material.RED_BUNDLE);
        BundleMeta bundleMeta = (BundleMeta) bundle.getItemMeta();
        bundleMeta.displayName(Messages.parse("<yellow>" + ShopCategory.INVESTMENTS.getDisplayName() + "</yellow>"));
        bundleMeta.lore(List.of(Messages.parse("<gray>Click to browse investments</gray>")));
        bundleMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bundle.setItemMeta(bundleMeta);
        inv.setItem(32, bundle);

        // Balance display
        long coins = getPlayerCoins(player);
        inv.setItem(53, createCoinDisplay(coins));

        // Cancel button
        inv.setItem(45, createCancelButton());

        player.openInventory(inv);
    }

    private static ItemStack createCategoryIcon(Material material, ShopCategory category) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<yellow>" + category.getDisplayName() + "</yellow>"));
        meta.lore(List.of(Messages.parse("<gray>Click to browse items</gray>")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCancelButton() {
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta meta = cancel.getItemMeta();
        meta.displayName(Messages.parse("<red>Cancel</red>"));
        cancel.setItemMeta(meta);
        return cancel;
    }

    private static ItemStack createCoinDisplay(long coins) {
        ItemStack coinItem = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = coinItem.getItemMeta();
        meta.displayName(Messages.parse("<green>Coins: <gold>$" + String.format("%,d", coins) + "</gold></green>"));
        coinItem.setItemMeta(meta);
        return coinItem;
    }

    private static long getPlayerCoins(Player player) {
        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return 0;
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        return ccp != null ? ccp.getCoins() : 0;
    }

    public static void openCategoryItems(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(
                new ShopHolder(null, category),
                54,
                Messages.parse("<gold><bold>" + category.getDisplayName() + "</bold></gold>")
        );

        ItemStack bg = backgroundPane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        switch (category) {
            case WEAPONS -> populateWeaponsCategory(inv, player);
            case ARMOR -> populateArmorCategory(inv, player);
            case FOOD -> populateFoodCategory(inv, player);
            case UTILITY -> populateUtilityCategory(inv, player);
            case CUSTOM_ITEMS -> populateCustomItemsCategory(inv, player);
            case ENCHANTS -> populateEnchantsCategory(inv, player);
            case INVESTMENTS -> populateInvestmentsCategory(inv, player);
            default -> populateGenericCategory(inv, player, category);
        }

        // Bottom row - Cancel, Undo, Coins
        addBottomRow(inv, player);

        player.openInventory(inv);
    }

    private static void populateWeaponsCategory(Inventory inv, Player player) {
        ShopItem swordItem = hasItem(player, Material.IRON_SWORD) ? ShopItem.DIAMOND_SWORD : ShopItem.IRON_SWORD;
        inv.setItem(10, createUpgradableItem(swordItem, hasItem(player, Material.DIAMOND_SWORD)));

        ShopItem axeItem = hasItem(player, Material.IRON_AXE) ? ShopItem.DIAMOND_AXE : ShopItem.IRON_AXE;
        inv.setItem(11, createUpgradableItem(axeItem, hasItem(player, Material.DIAMOND_AXE)));
    }

    private static void populateArmorCategory(Inventory inv, Player player) {
        // Helmet
        boolean hasIronHelmet = hasItem(player, Material.IRON_HELMET);
        boolean hasDiamondHelmet = hasItem(player, Material.DIAMOND_HELMET);
        if (hasDiamondHelmet) {
            inv.setItem(10, createMaxedItem(ShopItem.DIAMOND_HELMET));
        } else if (hasIronHelmet) {
            inv.setItem(10, createUpgradableItem(ShopItem.DIAMOND_HELMET, false));
        } else {
            inv.setItem(10, createUpgradableItem(ShopItem.IRON_HELMET, false));
        }

        // Chestplate
        boolean hasIronChest = hasItem(player, Material.IRON_CHESTPLATE);
        boolean hasDiamondChest = hasItem(player, Material.DIAMOND_CHESTPLATE);

        if (hasDiamondChest) {
            inv.setItem(11, createMaxedItem(ShopItem.DIAMOND_CHESTPLATE));
        } else if (hasIronChest) {
            inv.setItem(11, createUpgradableItem(ShopItem.DIAMOND_CHESTPLATE, false));
        } else {
            inv.setItem(11, createUpgradableItem(ShopItem.IRON_CHESTPLATE, false));
        }

        // Leggings
        boolean hasIronLegs = hasItem(player, Material.IRON_LEGGINGS);
        boolean hasDiamondLegs = hasItem(player, Material.DIAMOND_LEGGINGS);
        if (hasDiamondLegs) {
            inv.setItem(12, createMaxedItem(ShopItem.DIAMOND_LEGGINGS));
        } else if (hasIronLegs) {
            inv.setItem(12, createUpgradableItem(ShopItem.DIAMOND_LEGGINGS, false));
        } else {
            inv.setItem(12, createUpgradableItem(ShopItem.IRON_LEGGINGS, false));
        }

        // Boots
        boolean hasIronBoots = hasItem(player, Material.IRON_BOOTS);
        boolean hasDiamondBoots = hasItem(player, Material.DIAMOND_BOOTS);
        if (hasDiamondBoots) {
            inv.setItem(13, createMaxedItem(ShopItem.DIAMOND_BOOTS));
        } else if (hasIronBoots) {
            inv.setItem(13, createUpgradableItem(ShopItem.DIAMOND_BOOTS, false));
        } else {
            inv.setItem(13, createUpgradableItem(ShopItem.IRON_BOOTS, false));
        }


        inv.setItem(19, createShopItem(player, ShopItem.INVESTORS_HELMET));
        inv.setItem(20, createShopItem(player, ShopItem.INVESTORS_CHESTPLATE));
        inv.setItem(21, createShopItem(player, ShopItem.INVESTORS_LEGGINGS));
        inv.setItem(22, createShopItem(player, ShopItem.INVESTORS_BOOTS));

        inv.setItem(28, createShopItem(player, ShopItem.GILLIE_SUIT_HAT));
        inv.setItem(29, createShopItem(player, ShopItem.TAX_EVASION_PANTS));
        inv.setItem(30, createShopItem(player, ShopItem.GUARDIANS_VEST));
        inv.setItem(31, createShopItem(player, ShopItem.LIGHTFOOT_SHOES));

        inv.setItem(37, createShopItem(player, ShopItem.FLAMEBRINGER_LEGGINGS));
        inv.setItem(38, createShopItem(player, ShopItem.FLAMEBRINGER_BOOTS));

        inv.setItem(40, createShopItem(player, ShopItem.DEATHMAULER_OUTFIT));
        inv.setItem(41, createShopItem(player, ShopItem.DRAGON_SET));
    }

    private static void populateFoodCategory(Inventory inv, Player player) {
        int slot = 10;
        ShopItem[] foodItems = {
                ShopItem.BREAD, ShopItem.COOKED_MUTTON, ShopItem.STEAK, ShopItem.PORKCHOP,
                ShopItem.GOLDEN_CARROT, ShopItem.GOLDEN_APPLE, ShopItem.ENCHANTED_GOLDEN_APPLE
        };

        for (ShopItem item : foodItems) {
            if (slot >= 44) break;
            inv.setItem(slot++, createShopItem(player, item));
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
        }
    }

    private static void populateUtilityCategory(Inventory inv, Player player) {
        inv.setItem(20, createShopItem(player, ShopItem.LAVA_BUCKET));
        inv.setItem(21, createShopItem(player, ShopItem.FISHING_ROD));
        inv.setItem(22, createShopItem(player, ShopItem.COBWEB, 4));
        inv.setItem(24, createShopItem(player, ShopItem.CROSSBOW));
        inv.setItem(29, createShopItem(player, ShopItem.LAVA_BUCKET));
        inv.setItem(30, createShopItem(player, ShopItem.ENDER_PEARL));
        inv.setItem(31, createShopItem(player, ShopItem.WIND_CHARGE, 4));
        inv.setItem(33, createShopItem(player, ShopItem.BOW));
        inv.setItem(38, createShopItem(player, ShopItem.LEAVES, 16));
        inv.setItem(39, createShopItem(player, ShopItem.SOUL_SPEED_BLOCK));
        inv.setItem(42, createShopItem(player, ShopItem.ARROWS, 5));
    }

    private static void populateCustomItemsCategory(Inventory inv, Player player) {
        // Custom crafts would go here
        // For now, show a placeholder or redirect to a more specific category
        int slot = 10;

        // Add custom items here when implemented
        ItemStack placeholder = new ItemStack(Material.PAPER);
        ItemMeta meta = placeholder.getItemMeta();
        meta.displayName(Messages.parse("<gray>Custom items coming soon!</gray>"));
        placeholder.setItemMeta(meta);
        inv.setItem(slot, placeholder);
    }

    private static void populateEnchantsCategory(Inventory inv, Player player) {
        int slot = 10;
        for (EnchantEntry ee : EnchantEntry.values()) {
            if (slot >= 44) break;

            CashClashPlayer ccp = getCashClashPlayer(player);
            int currentLevel = ccp != null ? ccp.getOwnedEnchantLevel(ee) : 0;
            int nextLevel = currentLevel + 1;

            if (nextLevel > ee.getMaxLevel()) {
                // Maxed out
                inv.setItem(slot++, createMaxedEnchant(ee));
            } else {
                long price = ee.getPriceForLevel(nextLevel);
                inv.setItem(slot++, createEnchantItem(ee, nextLevel, price));
            }

            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }
    }

    private static void populateInvestmentsCategory(Inventory inv, Player player) {
        // Investments - Wallet, Purse, Ender bag
        int slot = 10;

        ItemStack wallet = new ItemStack(Material.PAPER);
        ItemMeta wm = wallet.getItemMeta();
        wm.displayName(Messages.parse("<yellow>Wallet</yellow>"));
        wm.lore(List.of(
                Messages.parse("<gray>Invest: <gold>$10,000</gold></gray>"),
                Messages.parse("<green>Bonus: $30,000</green>"),
                Messages.parse("<red>Negative: $5,000</red>")
        ));
        wm.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, "WALLET");
        wallet.setItemMeta(wm);
        inv.setItem(slot++, wallet);

        ItemStack purse = new ItemStack(Material.LEATHER);
        ItemMeta pm = purse.getItemMeta();
        pm.displayName(Messages.parse("<yellow>Purse</yellow>"));
        pm.lore(List.of(
                Messages.parse("<gray>Invest: <gold>$30,000</gold></gray>"),
                Messages.parse("<green>Bonus: $60,000</green>"),
                Messages.parse("<red>Negative: $10,000</red>")
        ));
        pm.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, "PURSE");
        purse.setItemMeta(pm);
        inv.setItem(slot++, purse);

        ItemStack enderBag = new ItemStack(Material.ENDER_CHEST);
        ItemMeta em = enderBag.getItemMeta();
        em.displayName(Messages.parse("<yellow>Ender Bag</yellow>"));
        em.lore(List.of(
                Messages.parse("<gray>Invest: <gold>$50,000</gold></gray>"),
                Messages.parse("<green>Bonus: $100,000</green>"),
                Messages.parse("<red>Negative: $20,000</red>")
        ));
        em.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, "ENDER_BAG");
        enderBag.setItemMeta(em);
        inv.setItem(slot, enderBag);
    }

    private static void populateGenericCategory(Inventory inv, Player player, ShopCategory category) {
        int slot = 10;
        List<ShopItem> items = Arrays.stream(ShopItem.values())
                .filter(i -> i.getCategory() == category)
                .toList();

        for (ShopItem si : items) {
            if (slot >= 44) break;
            inv.setItem(slot++, createShopItem(player, si));
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
        }
    }

    private static void addBottomRow(Inventory inv, Player player) {
        // Cancel button
        inv.setItem(45, createCancelButton());

        // Undo button
        ItemStack undo = new ItemStack(Material.ARROW);
        ItemMeta um = undo.getItemMeta();
        um.displayName(Messages.parse("<yellow>Undo Purchase</yellow>"));
        um.lore(List.of(Messages.parse("<gray>Undo last purchase and receive a refund</gray>")));
        undo.setItemMeta(um);
        inv.setItem(49, undo);

        // Coins display
        long coins = getPlayerCoins(player);
        inv.setItem(53, createCoinDisplay(coins));
    }

    private static ItemStack createShopItem(Player player, ShopItem item) {
        return createShopItem(player, item, 1);
    }

    private static ItemStack createShopItem(Player player, ShopItem item, int quantity) {
        ItemStack is = new ItemStack(item.getMaterial(), quantity);
        ItemMeta meta = is.getItemMeta();

        boolean owned = hasShopItem(player, item);

        if (owned) {
            meta.displayName(Messages.parse("<green>" + item.getDisplayName() + " <gray>(Owned)</gray></green>"));
            meta.lore(List.of(Messages.parse("<gray>You already own this item</gray>")));
        } else {
            meta.displayName(Messages.parse("<yellow>" + item.getDisplayName() + "</yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Messages.parse("<gray>Price: <gold>$" + String.format("%,d", item.getPrice()) + "</gold></gray>"));
            if (item.getInitialAmount() > 1) {
                lore.add(Messages.parse("<gray>Max: <white>" + item.getInitialAmount() + "</white></gray>"));
            }
            String desc = item.getDescription();
            if (!desc.isEmpty()) {
                lore.add(Messages.parse(desc));
            }
            lore.add(Component.empty());
            lore.add(Messages.parse("<yellow>Click to purchase!</yellow>"));
            meta.lore(lore);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, item.name());
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack createUpgradableItem(ShopItem item, boolean maxed) {
        ItemStack is = new ItemStack(item.getMaterial());
        ItemMeta meta = is.getItemMeta();

        if (maxed) {
            meta.displayName(Messages.parse("<green>" + item.getDisplayName() + " <gray>(Max)</gray></green>"));
            meta.lore(List.of(Messages.parse("<gray>Maximum tier reached!</gray>")));
        } else {
            meta.displayName(Messages.parse("<yellow>" + item.getDisplayName() + "</yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Messages.parse("<gray>Price: <gold>$" + String.format("%,d", item.getPrice()) + "</gold></gray>"));

            // Show upgrade path
            if (item.getMaterial().name().contains("IRON")) {
                lore.add(Messages.parse("<aqua>Next: Diamond tier</aqua>"));
            } else if (item.getMaterial().name().contains("DIAMOND")) {
                lore.add(Messages.parse("<light_purple>Final tier!</light_purple>"));
            }

            lore.add(Component.empty());
            lore.add(Messages.parse("<yellow>Click to purchase!</yellow>"));
            meta.lore(lore);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, item.name());
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack createMaxedItem(ShopItem item) {
        ItemStack is = new ItemStack(item.getMaterial());
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Messages.parse("<green>" + item.getDisplayName() + " <gray>(Max)</gray></green>"));
        meta.lore(List.of(Messages.parse("<gray>Maximum tier reached!</gray>")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, item.name());
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack createEnchantItem(EnchantEntry ee, int level, long price) {
        ItemStack is = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Messages.parse("<yellow>" + ee.getDisplayName() + " " + level + "</yellow>"));
        meta.lore(Arrays.asList(
                Messages.parse("<gray>Price: <gold>$" + String.format("%,d", price) + "</gold></gray>"),
                Messages.parse("<gray>Max Level: <white>" + ee.getMaxLevel() + "</white></gray>"),
                Component.empty(),
                Messages.parse("<yellow>Click to purchase!</yellow>")
        ));
        meta.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, ee.name());
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack createMaxedEnchant(EnchantEntry ee) {
        ItemStack is = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Messages.parse("<green>" + ee.getDisplayName() + " <gray>(Max)</gray></green>"));
        meta.lore(List.of(Messages.parse("<gray>Maximum level reached!</gray>")));
        meta.getPersistentDataContainer().set(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING, ee.name());
        is.setItemMeta(meta);
        return is;
    }


    private static boolean hasItem(Player player, Material material) {
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                return true;
            }
        }
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShopItem(Player player, ShopItem shopItem) {
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.hasItemMeta()) {
                String tag = is.getItemMeta().getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
                if (shopItem.name().equals(tag)) {
                    return true;
                }
            }
        }
        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.hasItemMeta()) {
                String tag = is.getItemMeta().getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
                if (shopItem.name().equals(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CashClashPlayer getCashClashPlayer(Player player) {
        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return null;
        return session.getCashClashPlayer(player.getUniqueId());
    }
}
