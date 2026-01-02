package me.psikuvit.cashClash.gui.builder;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Represents a fully built GUI ready to be opened.
 */
public record BuiltGui(Inventory inventory, GuiHolder holder) {

    /**
     * Open this GUI for a player.
     */
    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Get the inventory.
     */
    @Override
    public Inventory inventory() {
        return inventory;
    }

    /**
     * Get the holder.
     */
    @Override
    public GuiHolder holder() {
        return holder;
    }

    /**
     * Update an item at a specific slot.
     */
    public void updateItem(int slot, GuiButton button) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, button.getItem());
            holder.registerButton(slot, button);
        }
    }

    /**
     * Refresh the GUI for all viewers.
     */
    public void refresh() {
        inventory.getViewers().forEach(viewer -> {
            if (viewer instanceof Player player) {
                player.updateInventory();
            }
        });
    }
}
