package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Interface for all purchasable shop items.
 * Implemented by item enums like WeaponItem, ArmorItem, etc.
 */
public interface Purchasable {

    /**
     * Gets the material of this item.
     *
     * @return The Bukkit Material
     */
    Material getMaterial();

    /**
     * Gets the shop category this item belongs to.
     *
     * @return The shop category
     */
    ShopCategory getCategory();

    /**
     * Gets the price of this item.
     *
     * @return The price in game currency
     */
    long getPrice();

    /**
     * Gets the initial amount when purchased.
     *
     * @return The quantity given per purchase
     */
    int getInitialAmount();

    /**
     * Gets the display name for this item.
     *
     * @return The formatted display name
     */
    String getDisplayName();

    /**
     * Gets the description of this item.
     *
     * @return The item description, or empty string if none
     */
    String getDescription();

    /**
     * Gets the name of this item (enum constant name).
     * This method is provided by all enum implementations.
     *
     * @return The enum constant name
     */
    String name();
}

