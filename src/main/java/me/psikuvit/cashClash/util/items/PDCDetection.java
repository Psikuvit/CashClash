package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Centralized utility for detecting shop item types from ItemStacks.
 * All PDC-based type resolution and reading should route through these methods.
 */
public final class PDCDetection {

    private PDCDetection() {
        throw new AssertionError("Utility class");
    }

    // ==================== DIRECT TYPE GETTERS ====================

    public static Purchasable getPurchasable(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.valueOf(tag) : null;
    }

    /**
     * Get custom item type from an ItemStack.
     * @return CustomItem or null if not a custom item
     */
    public static CustomItem getCustomItem(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.getCustomItem(tag) : null;
    }

    /**
     * Get mythic item type from an ItemStack.
     * @return MythicItem or null if not a mythic
     */
    public static MythicItem getMythic(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.getMythic(tag) : null;
    }

    /**
     * Get custom armor type from an ItemStack.
     * @return CustomArmorItem or null if not custom armor
     */
    public static CustomArmorItem getCustomArmor(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.getCustomArmor(tag) : null;
    }

    /**
     * Get food item type from an ItemStack.
     * @return FoodItem or null if not a food item
     */
    public static FoodItem getFood(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.getFood(tag) : null;
    }

    public static InvestmentType getInvestment(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? ShopItems.getInvestment(tag) : null;
    }

    public static Byte getMaxedFlag(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_MAXED, PersistentDataType.BYTE);
    }

    public static EnchantEntry getEnchantEntry(ItemStack stack) {
        String tag = readTag(stack, Keys.ITEM_ID);
        return tag != null ? EnchantEntry.valueOf(tag) : null;
    }

    // ==================== ITEM TAG CHECKS ====================

    /**
     * Check if an item has any shop tag (ITEM_ID), indicating it was bought from shop.
     * @return true if item has a purchase tag
     */
    public static boolean hasPurchaseTag(ItemStack item) {
        return getAnyShopTag(item) != null;
    }

    /**
     * Check if an ItemStack is a custom armor item (any CustomArmorItem).
     * This includes individual pieces (Magic Helmet, Bunny Shoes, Tax Evasion Pants, Guardian's Vest)
     * and set pieces (Deathmauler, Dragon, Flamebridger, Investor sets).
     * @return true if item is custom armor
     */
    public static boolean isCustomArmorItem(ItemStack item) {
        return getCustomArmor(item) != null;
    }

    /**
     * Check if an ItemStack is a custom item.
     * @return true if item is a custom item
     */
    public static boolean isCustomItem(ItemStack item) {
        return getCustomItem(item) != null;
    }

    /**
     * Check if an ItemStack is a mythic item.
     * @return true if item is a mythic item
     */
    public static boolean isMythicItem(ItemStack item) {
        return getMythic(item) != null;
    }

    /**
     * Check if an ItemStack is a food item.
     * @return true if item is a food item
     */
    public static boolean isFoodItem(ItemStack item) {
        return getFood(item) != null;
    }

    /**
     * Check if the item tag matches a specific tag name.
     * @param item The item to check
     * @param tagName The tag name to match
     * @return true if the item's tag matches
     */
    public static boolean hasTag(ItemStack item, String tagName) {
        String tag = getAnyShopTag(item);
        return tag != null && tag.equals(tagName);
    }

    // ==================== OWNER TRACKING ====================

    /**
     * Get the owner UUID from an item (for grenades, bounce pads, etc.)
     * @return UUID or null if no owner set
     */
    public static UUID getItemOwner(ItemStack stack) {
        String ownerStr = readTag(stack, Keys.ITEM_OWNER);
        if (ownerStr == null) return null;
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if an item is owned by a specific player.
     * @param item The item to check
     * @param playerUuid The player's UUID
     * @return true if owned by the player
     */
    public static boolean isOwnedBy(ItemStack item, UUID playerUuid) {
        UUID owner = getItemOwner(item);
        return owner != null && owner.equals(playerUuid);
    }

    // ==================== USES TRACKING ====================

    /**
     * Get remaining uses from an item (for items with limited uses like Invis Cloak).
     * @return remaining uses or null if not tracked
     */
    public static Integer getItemUses(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_USES, PersistentDataType.INTEGER);
    }

    // ==================== SUPPLY DROP ====================

    /**
     * Get the supply drop amount from an item.
     * @return amount or null if not a supply drop
     */
    public static Integer getSupplyDropAmount(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER);
    }

    /**
     * Check if an item is a supply drop.
     * @return true if item is a supply drop
     */
    public static boolean isSupplyDrop(ItemStack stack) {
        return getSupplyDropAmount(stack) != null;
    }

    // ==================== MYTHIC WEAPON MODES ====================

    /**
     * Get the BlazeBite mode from an item.
     * @return "glacier", "volcano", or null if not set
     */
    public static String getBlazebiteMode(ItemStack stack) {
        return readTag(stack, Keys.BLAZEBITE_MODE);
    }

    /**
     * Get the BloodWrench mode from an item.
     * @return "rapid", "supercharged", or null if not set
     */
    public static String getBloodwrenchMode(ItemStack stack) {
        return readTag(stack, Keys.BLOODWRENCH_MODE);
    }

    // ==================== RAW TAG ACCESS ====================

    /**
     * Read a PDC string tag from an ItemStack.
     * @return tag value or null if not present
     */
    public static String readTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return null;
        if (!stack.hasItemMeta()) return null;

        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.STRING);
    }

    /**
     * Read a PDC byte tag from an ItemStack.
     * @return tag value or null if not present
     */
    public static Byte readByteTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return null;
        if (!stack.hasItemMeta()) return null;

        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.BYTE);
    }

    /**
     * Read a PDC integer tag from an ItemStack.
     * @return tag value or null if not present
     */
    public static Integer readIntTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return null;
        if (!stack.hasItemMeta()) return null;

        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.INTEGER);
    }

    /**
     * Check if an ItemStack has a specific PDC key.
     * @return true if the key exists
     */
    public static boolean hasKey(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!stack.hasItemMeta()) return false;

        return stack.getItemMeta().getPersistentDataContainer().has(key);
    }

    /**
     * Get the raw shop item tag from an ItemStack.
     * @return tag value or null
     */
    public static String getAnyShopTag(ItemStack stack) {
        return readTag(stack, Keys.ITEM_ID);
    }

    // ==================== ENTITY PDC DETECTION ====================

    /**
     * Check if an entity is a shop NPC.
     * @return true if the entity has the SHOP_NPC_KEY tag
     */
    public static boolean isShopNPC(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE);
    }

    /**
     * Check if an entity is an arena NPC (mannequin).
     * @return true if the entity has the ARENA_NPC_KEY tag
     */
    public static boolean isArenaNPC(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(Keys.ARENA_NPC_KEY, PersistentDataType.BYTE);
    }

    /**
     * Get the BlazeBite mode from an arrow entity.
     * @return "glacier", "volcano", or null if not set
     */
    public static String getArrowBlazebiteMode(Entity arrow) {
        if (arrow == null) return null;
        return arrow.getPersistentDataContainer().get(Keys.BLAZEBITE_MODE, PersistentDataType.STRING);
    }

    /**
     * Get the BloodWrench mode from an arrow entity.
     * @return "rapid", "supercharged", or null if not set
     */
    public static String getArrowBloodwrenchMode(Entity arrow) {
        if (arrow == null) return null;
        return arrow.getPersistentDataContainer().get(Keys.BLOODWRENCH_MODE, PersistentDataType.STRING);
    }
}

