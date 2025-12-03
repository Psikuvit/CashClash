package me.psikuvit.cashClash.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * InventoryHolder for the arena selection GUI so we can detect it safely without relying on title
 */
public class ArenaSelectionHolder implements InventoryHolder {
    private final Inventory inventory;

    public ArenaSelectionHolder(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

