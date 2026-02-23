package me.psikuvit.cashClash.util.items;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Provides lore for items from configuration.
 * Handles retrieving and parsing item lore from items.yml with fallback logic.
 */
public interface ItemLoreProvider {

    /**
     * Get the lore lines for an item.
     * Returns parsed MiniMessage components ready for display.
     *
     * @param category The item category (WEAPONS, ARMOR, FOOD, etc.)
     * @param itemKey The item key or enum name
     * @return List of lore components, or empty list if none configured
     */
    List<Component> getLore(String category, String itemKey);

    /**
     * Get the description for an item (single-purpose text).
     * Used for GUI tooltips and item descriptions.
     *
     * @param category The item category
     * @param itemKey The item key or enum name
     * @return Description component, or empty component if none configured
     */
    Component getDescription(String category, String itemKey);
}

