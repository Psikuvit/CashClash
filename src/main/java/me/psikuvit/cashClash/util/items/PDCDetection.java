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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Centralized utility for detecting shop item types from ItemStacks.
 * All PDC-based type resolution should route through these methods.
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
     * Get the raw shop item tag from an ItemStack.
     * @return tag value or null
     */
    public static String getAnyShopTag(ItemStack stack) {
        return readTag(stack, Keys.ITEM_ID);
    }
}

