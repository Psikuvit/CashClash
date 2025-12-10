package me.psikuvit.cashClash.shop;

import org.bukkit.Material;

/**
 * Shop categories and items
 */
public enum ShopCategory {
    WEAPONS,
    ARMOR,
    FOOD,
    UTILITY,
    ENCHANTS,
    CUSTOM_ITEMS,
    CUSTOM_ARMOR,
    LEGENDARIES,
    INVESTMENTS;

    public String getDisplayName() {
        return name().substring(0, 1).toUpperCase() + name().substring(1).replace("_", " ");
    }
}


