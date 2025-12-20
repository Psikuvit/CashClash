package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility class for creating GUI items in the shop system.
 * Provides factory methods for various item types with consistent styling.
 */
public final class GuiItemUtils {

    private GuiItemUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    // ==================== SHOP ITEMS ====================

    /**
     * Creates a shop item display with quantity 1.
     *
     * @param player The player viewing the shop
     * @param item   The purchasable item
     * @return The configured ItemStack for display
     */
    public static ItemStack createShopItem(Player player, Purchasable item) {
        return createShopItem(player, item, 1);
    }

    /**
     * Creates a shop item display with specified quantity.
     *
     * @param player   The player viewing the shop
     * @param item     The purchasable item
     * @param quantity The display quantity
     * @return The configured ItemStack for display
     */
    public static ItemStack createShopItem(Player player, Purchasable item, int quantity) {
        boolean owned = isItemOwned(player, item);

        if (owned) {
            return ShopItemBuilder.of(item.getMaterial(), quantity)
                    .name("<green>" + item.getDisplayName() + " <gray>(Owned)</gray></green>")
                    .owned()
                    .shopKey(item.name())
                    .build();
        }

        ShopItemBuilder builder = ShopItemBuilder.of(item.getMaterial(), quantity)
                .name("<yellow>" + item.getDisplayName() + "</yellow>")
                .price(item.getPrice());

        if (item.getInitialAmount() > 1) {
            builder.maxQuantity(item.getInitialAmount());
        }

        String desc = item.getDescription();
        if (desc != null && !desc.isEmpty()) {
            builder.description(desc);
        }

        return builder.purchasePrompt()
                .shopKey(item.name())
                .build();
    }

    // ==================== UPGRADABLE ITEMS ====================

    /**
     * Creates an upgradable item (armor/weapons) display.
     *
     * @param item  The purchasable item
     * @param maxed Whether the item is at max tier
     * @return The configured ItemStack for display
     */
    public static ItemStack createUpgradableItem(Purchasable item, boolean maxed) {
        if (maxed) {
            return ShopItemBuilder.of(item.getMaterial())
                    .name("<green>" + item.getDisplayName() + " <gray>(Max)</gray></green>")
                    .maxed("<gray>Maximum tier reached!</gray>")
                    .shopKey(item.name())
                    .build();
        }

        ShopItemBuilder builder = ShopItemBuilder.of(item.getMaterial())
                .name("<yellow>" + item.getDisplayName() + "</yellow>")
                .price(item.getPrice());

        // Show upgrade path based on material tier
        String materialName = item.getMaterial().name();
        if (materialName.contains("IRON")) {
            builder.nextTier("Diamond tier");
        } else if (materialName.contains("DIAMOND")) {
            builder.finalTier();
        }

        return builder.purchasePrompt()
                .shopKey(item.name())
                .build();
    }

    /**
     * Creates a locked diamond item display (for pre-round-4 restrictions).
     *
     * @param item         The diamond armor item
     * @param currentRound The current game round
     * @return The configured ItemStack for display
     */
    public static ItemStack createLockedDiamondItem(Purchasable item, int currentRound) {
        ConfigManager cfg = ConfigManager.getInstance();
        return ShopItemBuilder.of(item.getMaterial())
                .name("<red>" + item.getDisplayName() + " <gray>(Locked)</gray></red>")
                .price(item.getPrice())
                .emptyLine()
                .lore("<red>Diamond limit reached!</red>")
                .lore("<gray>Max " + cfg.getMaxDiamondPiecesEarly() +
                      " diamond pieces until Round " + cfg.getDiamondUnlockRound() + "</gray>")
                .lore("<gray>Current round: <yellow>" + currentRound + "</yellow></gray>")
                .shopKey(item.name())
                .build();
    }

    // ==================== ENCHANT ITEMS ====================

    /**
     * Creates an enchant book item display.
     *
     * @param enchant The enchantment entry
     * @param level   The next level to purchase
     * @param price   The price for this level
     * @return The configured ItemStack for display
     */
    public static ItemStack createEnchantItem(EnchantEntry enchant, int level, long price) {
        return ShopItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("<yellow>" + enchant.getDisplayName() + " " + level + "</yellow>")
                .price(price)
                .maxLevel(enchant.getMaxLevel())
                .purchasePrompt()
                .shopKey(enchant.name())
                .build();
    }

    /**
     * Creates a maxed enchant book display.
     *
     * @param enchant The enchantment entry
     * @return The configured ItemStack for display
     */
    public static ItemStack createMaxedEnchant(EnchantEntry enchant) {
        return ShopItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("<green>" + enchant.getDisplayName() + " <gray>(Max)</gray></green>")
                .maxed("<gray>Maximum level reached!</gray>")
                .shopKey(enchant.name())
                .build();
    }

    // ==================== CUSTOM ITEMS ====================

    /**
     * Creates a custom item icon for the shop display.
     *
     * @param type The custom item type
     * @return The configured ItemStack for display
     */
    public static ItemStack createCustomItemIcon(CustomItem type) {
        ShopItemBuilder builder = ShopItemBuilder.of(type.getMaterial())
                .name("<yellow>" + type.getDisplayName() + "</yellow>")
                .lore("<gold>Price: $" + String.format("%,d", type.getPrice()) + "</gold>")
                .emptyLine()
                .description(type.getDescription());

        if (type.hasLimit()) {
            builder.purchaseLimit(type.getMaxPurchase());
        }

        return builder.purchasePrompt()
                .customItemKey(type.name())
                .build();
    }

    // ==================== ARMOR SET ITEMS ====================

    /**
     * Creates an armor set icon that must be bought as a complete set.
     *
     * @param set    The armor set
     * @param player The player viewing the shop
     * @return The configured ItemStack for display
     */
    public static ItemStack createArmorSetIcon(CustomArmorItem.ArmorSet set, Player player) {
        Material iconMaterial = switch (set) {
            case INVESTORS -> Material.IRON_CHESTPLATE;
            case FLAMEBRINGER -> Material.DIAMOND_LEGGINGS;
            case DEATHMAULER -> Material.NETHERITE_CHESTPLATE;
            case DRAGON -> Material.DRAGON_HEAD;
        };

        boolean ownsSet = playerOwnsArmorSet(player, set);
        long totalPrice = set.getTotalPrice();

        if (ownsSet) {
            return ShopItemBuilder.of(iconMaterial)
                    .name("<green>" + set.getDisplayName() + " <gray>(Owned)</gray></green>")
                    .emptyLine()
                    .description(set.getDescription())
                    .emptyLine()
                    .lore("<green>✓ You own this set</green>")
                    .shopKey("SET_" + set.name())
                    .build();
        }

        ShopItemBuilder builder = ShopItemBuilder.of(iconMaterial)
                .name("<yellow>" + set.getDisplayName() + "</yellow>")
                .emptyLine()
                .lore("<dark_gray>Price:</dark_gray> <gold>$" + String.format("%,d", totalPrice) + "</gold>")
                .emptyLine()
                .description(set.getDescription())
                .emptyLine()
                .lore("<dark_gray>Includes:</dark_gray>");

        // Add list of pieces
        List<Component> lore = new ArrayList<>();
        for (CustomArmorItem piece : set.getPieces()) {
            lore.add(Messages.parse("<gray>  • " + piece.getDisplayName() + "</gray>"));
        }
        builder.emptyLine()
              .lore("<red>Must buy complete set!</red>")
              .purchasePrompt();

        ItemStack item = builder.shopKey("SET_" + set.name()).build();

        // Add piece lore manually since builder doesn't have a method for raw components
        var meta = item.getItemMeta();
        List<Component> currentLore = meta.lore();
        if (currentLore == null) {
            currentLore = new ArrayList<>();
        } else {
            currentLore = new ArrayList<>(currentLore);
        }
        currentLore.addAll(6, lore); // Insert after description
        meta.lore(currentLore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Checks if the player owns all pieces of an armor set.
     */
    private static boolean playerOwnsArmorSet(Player player, CustomArmorItem.ArmorSet set) {
        for (CustomArmorItem piece : set.getPieces()) {
            if (!hasCustomArmorPiece(player, piece)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the player has a specific custom armor piece (in inventory or equipped).
     */
    private static boolean hasCustomArmorPiece(Player player, CustomArmorItem piece) {
        return getAllInventoryItems(player)
                .filter(Objects::nonNull)
                .filter(ItemStack::hasItemMeta)
                .map(is -> is.getItemMeta().getPersistentDataContainer()
                        .get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING))
                .anyMatch(piece.name()::equals);
    }

    // ==================== INVESTMENT ITEMS ====================

    /**
     * Creates an investment item display.
     *
     * @param type The investment type
     * @return The configured ItemStack for display
     */
    public static ItemStack createInvestmentIcon(InvestmentType type) {
        Material iconMaterial = switch (type) {
            case WALLET -> Material.CHEST;
            case PURSE -> Material.PURPLE_BUNDLE;
            case ENDER_BAG -> Material.ENDER_CHEST;
        };

        return ShopItemBuilder.of(iconMaterial)
                .name("<yellow>" + type.name().replace("_", " ") + "</yellow>")
                .lore("<gray>Invest: <gold>$" + String.format("%,d", type.getCost()) + "</gold></gray>")
                .lore("<green>Bonus: $" + String.format("%,d", type.getBonusReturn()) + "</green>")
                .lore("<red>Negative: $" + String.format("%,d", type.getNegativeReturn()) + "</red>")
                .shopKey(type.name())
                .build();
    }

    // ==================== GUI CONTROL ITEMS ====================

    /**
     * Creates a shop category icon.
     *
     * @param material The icon material
     * @param category The shop category
     * @return The category icon ItemStack
     */
    public static ItemStack createCategoryIcon(Material material, ShopCategory category) {
        return ShopItemBuilder.of(material)
                .name("<yellow>" + category.getDisplayName() + "</yellow>")
                .lore("<gray>Click to browse items</gray>")
                .build();
    }

    /**
     * Creates a mythic/legendary item shop display.
     *
     * @param mythic The mythic item
     * @param playerHasMythic Whether player already owns a mythic
     * @param ownedMythic The mythic the player owns (can be null)
     * @param mythicTaken Whether this mythic is owned by someone else
     * @param ownerUuid The UUID of the owner (if taken)
     * @return The configured mythic shop item
     */
    public static ItemStack createMythicShopItem(me.psikuvit.cashClash.shop.items.MythicItem mythic,
                                                  boolean playerHasMythic,
                                                  me.psikuvit.cashClash.shop.items.MythicItem ownedMythic,
                                                  boolean mythicTaken,
                                                  java.util.UUID ownerUuid) {
        boolean isOwned = ownedMythic == mythic;

        ShopItemBuilder builder = ShopItemBuilder.of(mythic.getMaterial())
                .hideAttributes()
                .hideEnchants()
                .shopKey(mythic.name());

        if (isOwned) {
            // Player owns this mythic
            return builder
                    .name("<green><bold>" + mythic.getDisplayName() + "</bold></green>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine()
                    .lore("<dark_gray>" + mythic.getDescription() + "</dark_gray>")
                    .emptyLine()
                    .lore("<green>✓ You own this mythic</green>")
                    .build();
        } else if (mythicTaken) {
            // Another player owns this mythic
            String ownerName = ownerUuid != null ? org.bukkit.Bukkit.getOfflinePlayer(ownerUuid).getName() : "Someone";
            return builder
                    .name("<dark_red><bold>" + mythic.getDisplayName() + "</bold> <dark_gray>(Taken)</dark_gray></dark_red>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine()
                    .lore("<dark_gray>" + mythic.getDescription() + "</dark_gray>")
                    .emptyLine()
                    .lore("<red>✗ Owned by " + ownerName + "</red>")
                    .build();
        } else if (playerHasMythic) {
            // Player already has a different mythic
            return builder
                    .name("<gray><bold>" + mythic.getDisplayName() + "</bold> <dark_gray>(Locked)</dark_gray></gray>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine()
                    .lore("<dark_gray>" + mythic.getDescription() + "</dark_gray>")
                    .emptyLine()
                    .lore("<red>✗ You already own a mythic</red>")
                    .build();
        } else {
            // Available for purchase
            return builder
                    .name("<light_purple><bold>" + mythic.getDisplayName() + "</bold></light_purple>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine()
                    .lore("<dark_gray>Price:</dark_gray> <gold>$" + String.format("%,d", mythic.getPrice()) + "</gold>")
                    .emptyLine()
                    .lore("<gray>" + mythic.getDescription() + "</gray>")
                    .emptyLine()
                    .lore("<yellow>Click to purchase</yellow>")
                    .build();
        }
    }

    /**
     * Creates a cancel/back button.
     *
     * @return The cancel button ItemStack
     */
    public static ItemStack createCancelButton() {
        return ShopItemBuilder.of(Material.BARRIER)
                .name("<red>Cancel</red>")
                .lore("<gray>Close shop menu</gray>")
                .build();
    }

    /**
     * Creates an undo purchase button.
     *
     * @return The undo button ItemStack
     */
    public static ItemStack createUndoButton() {
        return ShopItemBuilder.of(Material.ARROW)
                .name("<yellow>Undo Purchase</yellow>")
                .lore("<gray>Undo last purchase and receive a refund</gray>")
                .build();
    }

    /**
     * Creates a coin balance display.
     *
     * @param coins The player's coin balance
     * @return The coin display ItemStack
     */
    public static ItemStack createCoinDisplay(long coins) {
        String formatted = String.format("%,d", coins);
        return ShopItemBuilder.of(Material.GOLD_INGOT)
                .name("<gold>Your Coins</gold>")
                .lore("<yellow>$" + formatted + "</yellow>")
                .build();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Checks if a player owns a specific shop item.
     * Uses single-pass stream for better performance.
     *
     * @param player   The player to check
     * @param shopItem The item to look for
     * @return true if the player owns the item
     */
    private static boolean isItemOwned(Player player, Purchasable shopItem) {
        // Only check ownership for categories where duplicates aren't allowed
        ShopCategory category = shopItem.getCategory();
        if (category != ShopCategory.ARMOR &&
            category != ShopCategory.WEAPONS &&
            category != ShopCategory.CUSTOM_ARMOR) {
            return false;
        }

        return getAllInventoryItems(player)
                .filter(Objects::nonNull)
                .filter(ItemStack::hasItemMeta)
                .map(is -> is.getItemMeta().getPersistentDataContainer()
                        .get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING))
                .anyMatch(shopItem.name()::equals);
    }

    /**
     * Gets all items from player inventory and armor slots as a single stream.
     *
     * @param player The player
     * @return Stream of all inventory items
     */
    private static Stream<ItemStack> getAllInventoryItems(Player player) {
        return Stream.concat(
                Arrays.stream(player.getInventory().getContents()),
                Arrays.stream(player.getInventory().getArmorContents())
        );
    }
}
