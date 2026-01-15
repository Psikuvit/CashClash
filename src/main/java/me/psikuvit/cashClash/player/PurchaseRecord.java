package me.psikuvit.cashClash.player;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Record of a purchase made by a player.
 * @param item The shop item that was purchased
 * @param quantity The quantity purchased
 * @param price The total price paid
 * @param replacedItem The item that was replaced (null if nothing was replaced) - for single items
 * @param round The round in which the purchase was made
 * @param replacedSetItems Map of armor slot to replaced item for set purchases (null for non-sets)
 * @param setItems List of set pieces purchased (null for non-sets)
 */
public record PurchaseRecord(
        Purchasable item,
        int quantity,
        long price,
        ItemStack replacedItem,
        int round,
        Map<ArmorSlot, ItemStack> replacedSetItems,
        List<CustomArmorItem> setItems
) {

    /**
     * Constructor for purchases that don't replace anything.
     */
    public PurchaseRecord(Purchasable item, int quantity, long price, int round) {
        this(item, quantity, price, null, round, null, null);
    }

    /**
     * Constructor for single item purchases with replacement.
     */
    public PurchaseRecord(Purchasable item, int quantity, long price, ItemStack replacedItem, int round) {
        this(item, quantity, price, replacedItem, round, null, null);
    }

    /**
     * Constructor for armor set purchases.
     */
    public PurchaseRecord(long price, int round, Map<ArmorSlot, ItemStack> replacedSetItems, List<CustomArmorItem> setItems) {
        this(null, 1, price, null, round, replacedSetItems, setItems);
    }

    /**
     * Check if this is a set purchase.
     */
    public boolean isSetPurchase() {
        return setItems != null && !setItems.isEmpty();
    }

    /**
     * Get the replaced set items, returning empty map if null.
     */
    public Map<ArmorSlot, ItemStack> getReplacedSetItemsSafe() {
        return replacedSetItems != null ? replacedSetItems : Collections.emptyMap();
    }

    /**
     * Get the set items, returning empty list if null.
     */
    public List<CustomArmorItem> getSetItemsSafe() {
        return setItems != null ? setItems : Collections.emptyList();
    }

    /**
     * Enum representing armor slots for set tracking.
     */
    public enum ArmorSlot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }
}

