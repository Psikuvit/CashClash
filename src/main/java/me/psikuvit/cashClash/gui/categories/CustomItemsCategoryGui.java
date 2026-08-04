package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomItem;
import org.bukkit.entity.Player;

/**
 * Shop category GUI for custom items (grenades, bounce pads, etc.).
 */
public class CustomItemsCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_custom_items";

    public CustomItemsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.CUSTOM_ITEMS);
    }

    @Override
    protected void populateItems() {
        // Row 3
        setButton(20, createCustomItemButton(CustomItem.BLOOMING_ROSE));
        setButton(21, createCustomItemButton(CustomItem.HUNTERS_MARK));
        setButton(22, createCustomItemButton(CustomItem.TOTEM_OF_HAUNTING));
        setButton(23, createCustomItemButton(CustomItem.ICE_FAN));
        setButton(24, createCustomItemButton(CustomItem.BOOMBOX));

        // Row 4
        setButton(29, createCustomItemButton(CustomItem.OVERDRIVE_POTION));
        setButton(30, createCustomItemButton(CustomItem.BOUNCE_PAD));
        setButton(31, createCustomItemButton(CustomItem.RADIATING_LOTUS));
        setButton(32, createCustomItemButton(CustomItem.INVIS_CLOAK));
        setButton(33, createCustomItemButton(CustomItem.ORB_OF_GRAVITATION));
    }
}
