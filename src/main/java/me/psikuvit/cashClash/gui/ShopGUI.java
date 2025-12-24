package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShopGUI {

    private static ItemStack backgroundPane() {
        return backgroundPane(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static ItemStack backgroundPane(Material material) {
        ItemStack pane = new ItemStack(material);
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
        inv.setItem(11, GuiItemUtils.createCategoryIcon(Material.IRON_AXE, ShopCategory.WEAPONS));
        inv.setItem(12, GuiItemUtils.createCategoryIcon(Material.DIAMOND_CHESTPLATE, ShopCategory.ARMOR));
        inv.setItem(13, GuiItemUtils.createCategoryIcon(Material.GOLDEN_APPLE, ShopCategory.FOOD));
        inv.setItem(14, GuiItemUtils.createCategoryIcon(Material.ENDER_PEARL, ShopCategory.UTILITY));
        inv.setItem(15, GuiItemUtils.createCategoryIcon(Material.NAME_TAG, ShopCategory.CUSTOM_ITEMS));

        // Row 3
        inv.setItem(21, GuiItemUtils.createCategoryIcon(Material.ENCHANTING_TABLE, ShopCategory.ENCHANTS));

        ItemStack bundle = new ItemStack(Material.RED_BUNDLE);
        BundleMeta bundleMeta = (BundleMeta) bundle.getItemMeta();

        bundleMeta.displayName(Messages.parse("<yellow>" + ShopCategory.INVESTMENTS.getDisplayName() + "</yellow>"));
        bundleMeta.lore(Messages.wrapLines("<gray>Click to browse investments</gray>"));
        bundleMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        bundle.setItemMeta(bundleMeta);
        inv.setItem(23, bundle);

        int[] legendSlots = {38, 39, 40, 41, 42};
        String[] legendColors = {"RED", "ORANGE", "YELLOW", "GREEN", "BLUE"};

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            List<MythicItem> availableMythics = MythicItemManager.getInstance().getAvailableLegendaries(session);

            UUID playerUuid = player.getUniqueId();
            boolean playerHasMythic = MythicItemManager.getInstance().hasPlayerPurchasedMythic(session, playerUuid);

            MythicItem ownedMythic = MythicItemManager.getInstance().getPlayerMythic(session, playerUuid);

            // Legendaries header
            ItemStack legendHeader = new ItemStack(Material.DRAGON_HEAD);
            ItemMeta legendHeaderMeta = legendHeader.getItemMeta();

            legendHeaderMeta.displayName(Messages.parse("<dark_purple><bold>✦ MYTHIC WEAPONS ✦</bold></dark_purple>"));

            List<Component> headerLore = new ArrayList<>();
            headerLore.add(Component.empty());

            headerLore.addAll(Messages.wrapLines("<gray>One per player. Each mythic is unique per game.</gray>"));
            legendHeaderMeta.lore(headerLore);
            legendHeaderMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            legendHeader.setItemMeta(legendHeaderMeta);
            inv.setItem(31, legendHeader);

            for (int i = 0; i < availableMythics.size() && i < legendSlots.length; i++) {
                MythicItem mythic = availableMythics.get(i);
                boolean mythicTaken = !MythicItemManager.getInstance().isMythicAvailableInSession(session, mythic);

                UUID ownerUuid = MythicItemManager.getInstance().getMythicOwner(session, mythic);
                inv.setItem(legendSlots[i], GuiItemUtils.createMythicShopItem(mythic, playerHasMythic, ownedMythic, mythicTaken, ownerUuid));

                String material = session.getCurrentRound() == 1 ? "GRAY_STAINED_GLASS_PANE" : legendColors[i] + "_STAINED_GLASS_PANE";

                ItemStack itemStack = new ItemStack(Material.valueOf(material));
                ItemMeta itemMeta = itemStack.getItemMeta();

                itemMeta.displayName(Component.empty());
                itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                itemStack.setItemMeta(itemMeta);
                inv.setItem(legendSlots[i] + 9, itemStack);
            }
        }


        // Balance display
        long coins = getPlayerCoins(player);
        inv.setItem(53, GuiItemUtils.createCoinDisplay(coins));

        // Cancel button
        inv.setItem(45, GuiItemUtils.createCancelButton());

        player.openInventory(inv);
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
            case CUSTOM_ITEMS -> populateCustomItemsCategory(inv);
            case ENCHANTS -> populateEnchantsCategory(inv, player);
            case INVESTMENTS -> populateInvestmentsCategory(inv);
        }

        // Bottom row - Cancel, Undo, Coins
        addBottomRow(inv, player);

        player.openInventory(inv);
    }

    private static void populateWeaponsCategory(Inventory inv, Player player) {
        boolean hasIronSword = hasItem(player, Material.IRON_SWORD);
        boolean hasDiamondSword = hasItem(player, Material.DIAMOND_SWORD);
        if (hasDiamondSword) {
            inv.setItem(23, GuiItemUtils.createUpgradableItem(WeaponItem.DIAMOND_SWORD, true));
        } else if (hasIronSword) {
            inv.setItem(23, GuiItemUtils.createUpgradableItem(WeaponItem.DIAMOND_SWORD, false));
        } else {
            inv.setItem(23, GuiItemUtils.createUpgradableItem(WeaponItem.IRON_SWORD, false));
        }

        boolean hasIronAxe = hasItem(player, Material.IRON_AXE);
        boolean hasDiamondAxe = hasItem(player, Material.DIAMOND_AXE);
        if (hasDiamondAxe) {
            inv.setItem(21, GuiItemUtils.createUpgradableItem(WeaponItem.DIAMOND_AXE, true));
        } else if (hasIronAxe) {
            inv.setItem(21, GuiItemUtils.createUpgradableItem(WeaponItem.DIAMOND_AXE, false));
        } else {
            inv.setItem(21, GuiItemUtils.createUpgradableItem(WeaponItem.IRON_AXE, false));
        }
    }

    private static void populateArmorCategory(Inventory inv, Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        int currentRound = session != null ? session.getCurrentRound() : 1;

        // Count current diamond armor pieces
        int diamondCount = 0;
        if (hasItem(player, Material.DIAMOND_HELMET)) diamondCount++;
        if (hasItem(player, Material.DIAMOND_CHESTPLATE)) diamondCount++;
        if (hasItem(player, Material.DIAMOND_LEGGINGS)) diamondCount++;
        if (hasItem(player, Material.DIAMOND_BOOTS)) diamondCount++;

        ConfigManager cfg = ConfigManager.getInstance();
        boolean diamondLimitReached = currentRound < cfg.getDiamondUnlockRound()
                && diamondCount >= cfg.getMaxDiamondPiecesEarly();


        placeArmorItem(inv, player, 10, ArmorItem.IRON_HELMET, ArmorItem.DIAMOND_HELMET,
                Material.IRON_HELMET, Material.DIAMOND_HELMET, diamondLimitReached, currentRound);
        placeArmorItem(inv, player, 19, ArmorItem.IRON_CHESTPLATE, ArmorItem.DIAMOND_CHESTPLATE,
                Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, diamondLimitReached, currentRound);
        placeArmorItem(inv, player, 28, ArmorItem.IRON_LEGGINGS, ArmorItem.DIAMOND_LEGGINGS,
                Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, diamondLimitReached, currentRound);
        placeArmorItem(inv, player, 37, ArmorItem.IRON_BOOTS, ArmorItem.DIAMOND_BOOTS,
                Material.IRON_BOOTS, Material.DIAMOND_BOOTS, diamondLimitReached, currentRound);

        ItemStack[] investorPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.INVESTORS, player);
        inv.setItem(11, investorPieces.length > 0 ? investorPieces[0] : null);  // Helmet
        inv.setItem(20, investorPieces.length > 1 ? investorPieces[1] : null); // Chestplate
        inv.setItem(29, investorPieces.length > 2 ? investorPieces[2] : null); // Leggings
        inv.setItem(38, investorPieces.length > 3 ? investorPieces[3] : null); // Boots

        for (int i = 0; i < 4; i++) {
            inv.setItem(12 + i * 9, backgroundPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        ItemStack[] deathmaulerPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.DEATHMAULER, player);
        inv.setItem(13, ItemStack.of(Material.BARRIER));
        inv.setItem(14, deathmaulerPieces.length > 0 ? deathmaulerPieces[0] : null);
        inv.setItem(15, deathmaulerPieces.length > 1 ? deathmaulerPieces[1] : null);
        inv.setItem(16, ItemStack.of(Material.BARRIER));

        ItemStack[] dragonPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.DRAGON, player);
        inv.setItem(22, dragonPieces.length > 0 ? dragonPieces[0] : null);
        inv.setItem(23, dragonPieces.length > 1 ? dragonPieces[1] : null);
        inv.setItem(24, ItemStack.of(Material.BARRIER));
        inv.setItem(25, dragonPieces.length > 2 ? dragonPieces[2] : null);

        ItemStack[] flamebringerPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.FLAMEBRINGER, player);
        inv.setItem(31, ItemStack.of(Material.BARRIER));
        inv.setItem(32, flamebringerPieces.length > 0 ? flamebringerPieces[0] : null);
        inv.setItem(33, flamebringerPieces.length > 1 ? flamebringerPieces[1] : null);
        inv.setItem(34, ItemStack.of(Material.BARRIER));

        // === COLUMN 6: Individual Custom Armor Pieces ===
        inv.setItem(40, GuiItemUtils.createShopItem(player, CustomArmorItem.MAGIC_HELMET));
        inv.setItem(41, GuiItemUtils.createShopItem(player, CustomArmorItem.BUNNY_SHOES));
        inv.setItem(42, GuiItemUtils.createShopItem(player, CustomArmorItem.GUARDIANS_VEST));
        inv.setItem(43, GuiItemUtils.createShopItem(player, CustomArmorItem.TAX_EVASION_PANTS));
    }

    private static void placeArmorItem(Inventory inv, Player player, int slot,
                                        ArmorItem ironItem, ArmorItem diamondItem,
                                        Material ironMaterial, Material diamondMaterial,
                                        boolean diamondLimitReached, int currentRound) {
        boolean hasIron = hasItem(player, ironMaterial);
        boolean hasDiamond = hasItem(player, diamondMaterial);

        if (hasDiamond) {
            inv.setItem(slot, GuiItemUtils.createUpgradableItem(diamondItem, true));
        } else if (hasIron) {
            if (diamondLimitReached) {
                inv.setItem(slot, GuiItemUtils.createLockedDiamondItem(diamondItem, currentRound));
            } else {
                inv.setItem(slot, GuiItemUtils.createUpgradableItem(diamondItem, false));
            }
        } else {
            inv.setItem(slot, GuiItemUtils.createUpgradableItem(ironItem, false));
        }
    }

    private static void populateFoodCategory(Inventory inv, Player player) {
        inv.setItem(19, GuiItemUtils.createShopItem(player, FoodItem.BREAD, 4));
        inv.setItem(20, GuiItemUtils.createShopItem(player, FoodItem.COOKED_MUTTON, 4));
        inv.setItem(21, GuiItemUtils.createShopItem(player, FoodItem.STEAK, 4));
        inv.setItem(22, GuiItemUtils.createShopItem(player, FoodItem.PORKCHOP, 4));
        inv.setItem(23, GuiItemUtils.createShopItem(player, FoodItem.GOLDEN_CARROT, 4));
        inv.setItem(25, GuiItemUtils.createShopItem(player, FoodItem.GOLDEN_APPLE));
        inv.setItem(28, GuiItemUtils.createShopItem(player, FoodItem.SPEED_CARROT, 2));
        inv.setItem(29, GuiItemUtils.createShopItem(player, FoodItem.GOLDEN_CHICKEN, 2));
        inv.setItem(30, GuiItemUtils.createShopItem(player, FoodItem.COOKIE_OF_LIFE, 2));
        inv.setItem(31, GuiItemUtils.createShopItem(player, FoodItem.SUNSCREEN, 2));
        inv.setItem(32, GuiItemUtils.createShopItem(player, FoodItem.CAN_OF_SPINACH, 2));
        inv.setItem(34, GuiItemUtils.createShopItem(player, FoodItem.ENCHANTED_GOLDEN_APPLE));
    }

    private static void populateUtilityCategory(Inventory inv, Player player) {
        inv.setItem(11, GuiItemUtils.createShopItem(player, UtilityItem.LAVA_BUCKET));
        inv.setItem(12, GuiItemUtils.createShopItem(player, UtilityItem.FISHING_ROD));
        inv.setItem(13, GuiItemUtils.createShopItem(player, UtilityItem.COBWEB, 4));
        inv.setItem(15, GuiItemUtils.createShopItem(player, UtilityItem.CROSSBOW));
        inv.setItem(20, GuiItemUtils.createShopItem(player, UtilityItem.LEAVES, 16));
        inv.setItem(21, GuiItemUtils.createShopItem(player, UtilityItem.WATER_BUCKET));
        inv.setItem(22, GuiItemUtils.createShopItem(player, UtilityItem.WIND_CHARGE, 4));
        inv.setItem(24, GuiItemUtils.createShopItem(player, UtilityItem.BOW));
        inv.setItem(30, GuiItemUtils.createShopItem(player, UtilityItem.SOUL_SAND, 16));
        inv.setItem(33, GuiItemUtils.createShopItem(player, UtilityItem.ARROWS, 5));
    }

    private static void populateCustomItemsCategory(Inventory inv) {
        inv.setItem(20, GuiItemUtils.createCustomItemIcon(CustomItem.GRENADE));
        inv.setItem(21, GuiItemUtils.createCustomItemIcon(CustomItem.SMOKE_CLOUD_GRENADE));
        inv.setItem(22, GuiItemUtils.createCustomItemIcon(CustomItem.BAG_OF_POTATOES));
        inv.setItem(23, GuiItemUtils.createCustomItemIcon(CustomItem.CASH_BLASTER));
        inv.setItem(24, GuiItemUtils.createCustomItemIcon(CustomItem.BOUNCE_PAD));
        inv.setItem(29, GuiItemUtils.createCustomItemIcon(CustomItem.BOOMBOX));
        inv.setItem(30, GuiItemUtils.createCustomItemIcon(CustomItem.MEDIC_POUCH));
        inv.setItem(31, GuiItemUtils.createCustomItemIcon(CustomItem.INVIS_CLOAK));
        inv.setItem(32, GuiItemUtils.createCustomItemIcon(CustomItem.TABLET_OF_HACKING));
        inv.setItem(33, GuiItemUtils.createCustomItemIcon(CustomItem.RESPAWN_ANCHOR));
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
                inv.setItem(slot++, GuiItemUtils.createMaxedEnchant(ee));
            } else {
                long price = ee.getPriceForLevel(nextLevel);
                inv.setItem(slot++, GuiItemUtils.createEnchantItem(ee, nextLevel, price));
            }

            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }
    }

    private static void populateInvestmentsCategory(Inventory inv) {
        inv.setItem(20, GuiItemUtils.createInvestmentIcon(InvestmentType.WALLET));
        inv.setItem(22, GuiItemUtils.createInvestmentIcon(InvestmentType.PURSE));
        inv.setItem(24, GuiItemUtils.createInvestmentIcon(InvestmentType.ENDER_BAG));
    }

    private static void addBottomRow(Inventory inv, Player player) {
        // Cancel button
        inv.setItem(45, GuiItemUtils.createCancelButton());

        // Undo button
        inv.setItem(49, GuiItemUtils.createUndoButton());

        // Coins display
        long coins = getPlayerCoins(player);
        inv.setItem(53, GuiItemUtils.createCoinDisplay(coins));
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

    private static CashClashPlayer getCashClashPlayer(Player player) {
        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return null;
        return session.getCashClashPlayer(player.getUniqueId());
    }
}
