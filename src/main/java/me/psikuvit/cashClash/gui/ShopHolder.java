package me.psikuvit.cashClash.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * InventoryHolder for shop GUIs to avoid using deprecated getTitle()
 *
 * @param type "categories" or "category:<name>"
 */
public class ShopHolder implements InventoryHolder {

    private final Inventory inventory;
    private final GuiType type;
    private final ShopCategory category;

    public ShopHolder(Inventory inventory, GuiType type) {
        this.inventory = inventory;
        this.type = type;
        this.category = null;
    }

    public ShopHolder(Inventory inventory, ShopCategory shopCategory) {
        this.inventory = inventory;
        this.type = GuiType.CATEGORY;
        this.category = shopCategory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

