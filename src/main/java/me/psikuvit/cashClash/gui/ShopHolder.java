package me.psikuvit.cashClash.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * InventoryHolder for shop GUIs to avoid using deprecated getTitle()
 *
 * @param type "categories" or "category:<name>"
 */
public record ShopHolder(Inventory inventory, String type) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

