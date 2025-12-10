package me.psikuvit.cashClash.player;

import me.psikuvit.cashClash.shop.ShopItem;
import org.bukkit.inventory.ItemStack;

/**
 * Record of a purchase made by a player.
 * @param item The shop item that was purchased
 * @param quantity The quantity purchased
 * @param price The total price paid
 * @param replacedItem The item that was replaced (null if nothing was replaced)
 */
public record PurchaseRecord(ShopItem item, int quantity, long price, ItemStack replacedItem) {

    /**
     * Constructor for purchases that don't replace anything.
     */
    public PurchaseRecord(ShopItem item, int quantity, long price) {
        this(item, quantity, price, null);
    }
}

