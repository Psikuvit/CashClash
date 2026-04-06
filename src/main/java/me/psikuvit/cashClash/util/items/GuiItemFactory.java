package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * Factory for creating GUI display items (shop items, buttons, etc.).
 * These items are used for display purposes only and are not meant to be used in-game.
 */
public final class GuiItemFactory {
    
    /**
     * Creates a shop item display with quantity 1.
     * 
     * @param player The player viewing the shop
     * @param item The purchasable item
     * @return The configured ItemStack for display
     */
    public ItemStack createShopItem(Player player, Purchasable item) {
        return createShopItem(player, item, 1);
    }
    
    /**
     * Creates a shop item display with specified quantity.
     * 
     * @param player The player viewing the shop
     * @param item The purchasable item
     * @param quantity The display quantity
     * @return The configured ItemStack for display
     */
    public ItemStack createShopItem(Player player, Purchasable item, int quantity) {
        boolean owned = ItemUtils.isItemOwned(player, item);

        if (owned) {
            return ShopItemBuilder.of(item.getMaterial(), quantity)
                    .name("<green>" + item.getDisplayName() + " <gray>(Owned)</gray></green>")
                    .owned()
                    .itemId(item.name())
                    .build();
        }

        ShopItemBuilder builder = ShopItemBuilder.of(item.getMaterial(), quantity)
                .name("<yellow>" + item.getDisplayName() + "</yellow>");

        // Display total price for stack items (quantity > 1)
        if (quantity > 1) {
            long totalPrice = item.getPrice() * quantity;
            builder.price(totalPrice);
            builder.priceDetail("<gray>Price per item: $" + String.format("%,d", item.getPrice()) + "</gray>");
        } else {
            builder.price(item.getPrice());
        }

        // Use ItemsConfig to get lore based on category and configKey
        String category = getCategoryForLore(item);
        String configKey = item.getConfigKey();
        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore(category, configKey);

        if (!loreLinesFromConfig.isEmpty()) {
            // Add lore from config (as individual lines, no wrapping)
            builder.configLore(loreLinesFromConfig);
        }

        return builder.purchasePrompt()
                .itemId(item.name())
                .build();
    }
    
    /**
     * Gets the lore category key for an item based on its type.
     * This maps Purchasable types to configuration category names.
     *
     * @param item The purchasable item
     * @return The category key for lore configuration (e.g., "weapons", "armor")
     */
    private String getCategoryForLore(Purchasable item) {
        switch (item) {
            case CustomArmorItem ignored -> {
                return "custom-armor";
            }
            case CustomItem ignored -> {
                return "custom-items";
            }
            case MythicItem ignored -> {
                return "mythic-items";
            }
            default -> {
                // Use the ShopCategory for standard items
                ShopCategory cat = item.getCategory();
                return switch (cat) {
                    case WEAPONS -> "weapons";
                    case ARMOR -> "armor";
                    case FOOD -> "food";
                    case UTILITY -> "utility";
                    case ENCHANTS -> "enchants";
                    case INVESTMENTS -> "investments";
                    case CUSTOM_ITEMS -> "custom-items";
                    case LEGENDARIES -> "mythic-items";
                    default -> "";
                };
            }
        }
    }

    /**
     * Creates an upgradable item (armor/weapons) display.
     * 
     * @param item The purchasable item
     * @param maxed Whether the item is at max tier
     * @return The configured ItemStack for display
     */
    public ItemStack createUpgradableItem(Purchasable item, boolean maxed) {
        if (maxed) {
            return ShopItemBuilder.of(item.getMaterial())
                    .name("<green>" + item.getDisplayName() + " <gray>(Max)</gray></green>")
                    .maxed("<gray>Maximum tier reached!</gray>")
                    .itemId(item.name())
                    .build();
        }

        ShopItemBuilder builder = ShopItemBuilder.of(item.getMaterial())
                .name("<yellow>" + item.getDisplayName() + "</yellow>")
                .price(item.getPrice());

        String category = getCategoryForLore(item);
        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore(category, item.getConfigKey());
        if (!loreLinesFromConfig.isEmpty()) {
            builder.configLore(loreLinesFromConfig);
        }

        // Show upgrade path based on material tier
        String materialName = item.getMaterial().name();
        if (materialName.contains("IRON")) {
            builder.nextTier("Diamond tier");
        } else if (materialName.contains("DIAMOND")) {
            builder.finalTier();
        }

        return builder.purchasePrompt()
                .itemId(item.name())
                .build();
    }
    
    /**
     * Creates a locked diamond item display (for pre-round-4 restrictions).
     * 
     * @param item The diamond armor item
     * @param currentRound The current game round
     * @return The configured ItemStack for display
     */
    public ItemStack createLockedDiamondItem(Purchasable item, int currentRound) {
        ConfigManager cfg = ConfigManager.getInstance();
        return ShopItemBuilder.of(item.getMaterial())
                .name("<red>" + item.getDisplayName() + " <gray>(Locked)</gray></red>")
                .price(item.getPrice())
                .emptyLine()
                .lore("<red>Diamond limit reached!</red>")
                .lore("<gray>Max " + cfg.getMaxDiamondPiecesEarly() +
                      " diamond pieces until Round " + cfg.getDiamondUnlockRound() + "</gray>")
                .lore("<gray>Current round: <yellow>" + currentRound + "</yellow></gray>")
                .itemId(item.name())
                .build();
    }
    
    /**
     * Creates an enchant book item display.
     * 
     * @param enchant The enchantment entry
     * @param level The next level to purchase
     * @param price The price for this level
     * @return The configured ItemStack for display
     */
    public ItemStack createEnchantItem(EnchantEntry enchant, int level, long price) {
        ShopItemBuilder builder = ShopItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("<yellow>" + enchant.getDisplayName() + " " + level + "</yellow>")
                .price(price)
                .maxLevel(enchant.getMaxLevel());

        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore("enchants", enchant.getConfigKey());
        if (!loreLinesFromConfig.isEmpty()) {
            builder.configLore(loreLinesFromConfig);
        }

        return builder.purchasePrompt()
                .itemId(enchant.name())
                .build();
    }
    
    /**
     * Creates a maxed enchant book display.
     * 
     * @param enchant The enchantment entry
     * @return The configured ItemStack for display
     */
    public ItemStack createMaxedEnchant(EnchantEntry enchant) {
        return ShopItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("<green>" + enchant.getDisplayName() + " <gray>(Max)</gray></green>")
                .maxed("<gray>Maximum level reached!</gray>")
                .itemId(enchant.name())
                .build();
    }
    
    /**
     * Creates a custom item icon for the shop display.
     * 
     * @param type The custom item type
     * @return The configured ItemStack for display
     */
    public ItemStack createCustomItemIcon(CustomItem type) {
        String category = getCategoryForLore(type);
        String configKey = type.getConfigKey();
        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore(category, configKey);

        ShopItemBuilder builder = ShopItemBuilder.of(type.getMaterial())
                .name("<yellow>" + type.getDisplayName() + "</yellow>")
                .price(type.getPrice())
                .emptyLine();

        if (!loreLinesFromConfig.isEmpty()) {
            // Add lore from config (as individual lines, no wrapping)
            builder.configLore(loreLinesFromConfig);
        }

        if (type.hasLimit()) {
            builder.purchaseLimit(type.getMaxPurchase());
        }

        return builder.purchasePrompt()
                .itemId(type.name())
                .build();
    }
    
    /**
     * Creates individual armor set piece items for display in a row.
     * Each piece when clicked will purchase the entire set.
     * 
     * @param set The armor set
     * @param player The player viewing the shop
     * @return Array of ItemStacks for each piece in the set
     */
    public ItemStack[] createArmorSetPieces(CustomArmorItem.ArmorSet set, Player player) {
        List<CustomArmorItem> pieces = set.getPieces();
        ItemStack[] items = new ItemStack[pieces.size()];

        boolean ownsSet = ItemUtils.playerOwnsArmorSet(player, set);
        long totalPrice = set.getTotalPrice();

        for (int i = 0; i < pieces.size(); i++) {
            CustomArmorItem piece = pieces.get(i);

            // Get lore from config
            String category = getCategoryForLore(piece);
            String configKey = piece.getConfigKey();
            List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore(category, configKey);

            if (ownsSet) {
                ShopItemBuilder builder = ShopItemBuilder.of(piece.getMaterial())
                        .name("<green>" + piece.getDisplayName() + " <gray>(Owned)</gray></green>")
                        .lore("<dark_purple>" + set.getDisplayName() + " Set</dark_purple>")
                        .emptyLine();

                if (!loreLinesFromConfig.isEmpty()) {
                    builder.configLore(loreLinesFromConfig);
                }

                items[i] = builder
                        .emptyLine()
                        .lore("<green>✓ Set owned</green>")
                        .itemId("SET_" + set.name())
                        .build();
            } else {
                ShopItemBuilder builder = ShopItemBuilder.of(piece.getMaterial())
                        .name("<yellow>" + piece.getDisplayName() + "</yellow>")
                        .lore("<dark_purple>" + set.getDisplayName() + " Set</dark_purple>")
                        .emptyLine();

                if (!loreLinesFromConfig.isEmpty()) {
                    builder.configLore(loreLinesFromConfig);
                }

                items[i] = builder
                        .emptyLine()
                        .lore("<red>⚠ Must buy complete set!</red>")
                        .emptyLine()
                        .lore("<yellow>Click to purchase entire set</yellow>")
                        .priceDetail("<dark_gray>Piece Price:</dark_gray> <gray>$" + String.format("%,d", piece.getPrice()) + "</gray>")
                        .priceDetail("<dark_gray>Set Total:</dark_gray> <gold>$" + String.format("%,d", totalPrice) + "</gold>")
                        .itemId("SET_" + set.name())
                        .build();
            }
        }

        return items;
    }
    
    /**
     * Creates an investment item display.
     * 
     * @param type The investment type
     * @return The configured ItemStack for display
     */
    public ItemStack createInvestmentIcon(InvestmentType type) {
        ShopItemBuilder builder = ShopItemBuilder.of(type.getMaterial())
                .name("<yellow>" + type.name().replace("_", " ") + "</yellow>")
                .price(type.getCost());

        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore("investments", type.getConfigKey());
        if (!loreLinesFromConfig.isEmpty()) {
            builder.configLore(loreLinesFromConfig);
        }

        builder.priceDetail("<green>Bonus: $" + String.format("%,d", type.getBonusReturn()) + "</green>")
                .priceDetail("<red>Negative: $" + String.format("%,d", type.getNegativeReturn()) + "</red>");

        ItemStack item = builder
                .itemId(type.name())
                .build();

        // Apply custom model data for investment items
        CustomModelDataMapper.applyCustomModel(item, type);

        return item;
    }
    
    /**
     * Creates a shop category icon.
     * 
     * @param material The icon material
     * @param category The shop category
     * @return The category icon ItemStack
     */
    public ItemStack createCategoryIcon(Material material, ShopCategory category) {
        String categoryKey = category.name().toLowerCase().replace("_", "-");
        String defaultName = "<yellow>" + category.getDisplayName() + "</yellow>";
        String configuredName = ItemsConfig.getInstance().getCategoryName(categoryKey, defaultName);
        List<String> configuredLore = ItemsConfig.getInstance().getCategoryLore(categoryKey);

        ShopItemBuilder builder = ShopItemBuilder.of(material)
                .name(configuredName);

        if (!configuredLore.isEmpty()) {
            builder.configLore(configuredLore);
        } else {
            builder.lore("<gray>Click to browse items</gray>");
        }

        return builder.build();
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
    public ItemStack createMythicShopItem(MythicItem mythic,
                                         boolean playerHasMythic,
                                         MythicItem ownedMythic,
                                         boolean mythicTaken,
                                         UUID ownerUuid) {
        boolean isOwned = ownedMythic != null && ownedMythic == mythic;
        
        // Get lore from config
        String category = getCategoryForLore(mythic);
        String configKey = mythic.getConfigKey();
        List<String> loreLinesFromConfig = ItemsConfig.getInstance().getItemLore(category, configKey);

        ShopItemBuilder builder = ShopItemBuilder.of(Material.BARRIER)
                .hideAttributes()
                .hideEnchants()
                .itemId(mythic.name());
        
        if (isOwned) {
            // Player owns this mythic
            builder.name("<green><bold>" + mythic.getDisplayName() + "</bold></green>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine();

            if (!loreLinesFromConfig.isEmpty()) {
                builder.configLore(loreLinesFromConfig);
            }

            return builder
                    .emptyLine()
                    .lore("<green>✓ You own this mythic</green>")
                    .build();
        } else if (mythicTaken) {
            // Another player owns this mythic
            String ownerName = ownerUuid != null ? Bukkit.getOfflinePlayer(ownerUuid).getName() : "Someone";
            builder.name("<dark_red><bold>" + mythic.getDisplayName() + "</bold> <dark_gray>(Taken)</dark_gray></dark_red>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine();

            if (!loreLinesFromConfig.isEmpty()) {
                builder.configLore(loreLinesFromConfig);
            }

            return builder
                    .emptyLine()
                    .lore("<red>✗ Owned by " + ownerName + "</red>")
                    .build();
        } else if (playerHasMythic) {
            // Player already has a different mythic
            builder.name("<gray><bold>" + mythic.getDisplayName() + "</bold> <dark_gray>(Locked)</dark_gray></gray>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine();

            if (!loreLinesFromConfig.isEmpty()) {
                builder.configLore(loreLinesFromConfig);
            }

            return builder
                    .emptyLine()
                    .lore("<red>✗ You already own a mythic</red>")
                    .build();
        } else {
            // Available for purchase
            return ShopItemBuilder.of(mythic.getMaterial())
                    .hideAttributes()
                    .hideEnchants()
                    .itemId(mythic.name())
                    .name("<light_purple><bold>" + mythic.getDisplayName() + "</bold></light_purple>")
                    .lore("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>")
                    .emptyLine()
                    .configLore(loreLinesFromConfig)
                    .emptyLine()
                    .lore("<yellow>Click to purchase</yellow>")
                    .price(mythic.getPrice())
                    .build();
        }
    }
    
    /**
     * Creates a cancel/back button.
     * 
     * @return The cancel button ItemStack
     */
    public ItemStack createCancelButton() {
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
    public ItemStack createUndoButton() {
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
    public ItemStack createCoinDisplay(long coins) {
        String formatted = String.format("%,d", coins);
        ItemStack item = ShopItemBuilder.of(Material.SUNFLOWER)
                .name("<gold>Your Coins</gold>")
                .lore("<yellow>$" + formatted + "</yellow>")
                .build();
        
        // Apply custom model data for cash coins texture
        CustomModelDataMapper.applyStringModelData(item, CustomModelDataMapper.CASH_COINS_KEY);
        
        return item;
    }
    
    /**
     * Creates a player head item representing the target player.
     * 
     * @param target The target player
     * @param displayName The display name for the head
     * @param lore The lore lines for the head
     * @return The player head ItemStack
     */
    public ItemStack createPlayerHead(Player target, String displayName, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        meta.setPlayerProfile(target.getPlayerProfile());
        meta.displayName(Messages.parse(displayName));
        meta.lore(lore.stream().map(Messages::parse).toList());
        
        skull.setItemMeta(meta);
        return skull;
    }
}
