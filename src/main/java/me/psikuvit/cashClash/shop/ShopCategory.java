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
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }
}


