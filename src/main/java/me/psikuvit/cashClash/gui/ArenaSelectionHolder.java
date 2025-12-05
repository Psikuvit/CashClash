package me.psikuvit.cashClash.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * InventoryHolder for the arena selection GUI so we can detect it safely without relying on title
 */
public record ArenaSelectionHolder(Inventory inventory) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

