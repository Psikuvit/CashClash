package me.psikuvit.cashClash.gui.builder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * InventoryHolder for GUI Builder system.
 * Stores button click handlers for slot-based action resolution.
 */
public class GuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final String guiId;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private boolean allowPlayerInventoryClick = false;

    public GuiHolder(String guiId) {
        this.guiId = guiId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Get the unique GUI identifier.
     */
    public String getGuiId() {
        return guiId;
    }

    /**
     * Register a button at a specific slot.
     */
    public void registerButton(int slot, GuiButton button) {
        buttons.put(slot, button);
    }

    /**
     * Get the button at a specific slot.
     */
    public GuiButton getButton(int slot) {
        return buttons.get(slot);
    }

    /**
     * Check if a slot has a button registered.
     */
    public boolean hasButton(int slot) {
        return buttons.containsKey(slot);
    }

    /**
     * Get all registered buttons.
     */
    public Map<Integer, GuiButton> getButtons() {
        return buttons;
    }

    /**
     * Check if player inventory clicks are allowed.
     */
    public boolean isPlayerInventoryClickAllowed() {
        return allowPlayerInventoryClick;
    }

    /**
     * Set whether player inventory clicks are allowed.
     */
    public void setAllowPlayerInventoryClick(boolean allow) {
        this.allowPlayerInventoryClick = allow;
    }
}

