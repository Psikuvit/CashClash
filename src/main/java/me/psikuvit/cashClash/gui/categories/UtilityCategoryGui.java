package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import org.bukkit.entity.Player;

/**
 * Shop category GUI for utility items.
 */
public class UtilityCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_utility";

    public UtilityCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.UTILITY);
    }

    @Override
    protected void populateItems() {
        // Row 2
        setButton(11, createShopItemButton(UtilityItem.LAVA_BUCKET, 1));
        setButton(12, createShopItemButton(UtilityItem.FISHING_ROD, 1));
        setButton(13, createShopItemButton(UtilityItem.COBWEB, 4));
        setButton(15, createShopItemButton(UtilityItem.CROSSBOW, 1));

        // Row 3
        setButton(20, createShopItemButton(UtilityItem.LEAVES, 16));
        setButton(21, createShopItemButton(UtilityItem.WATER_BUCKET, 1));
        setButton(22, createShopItemButton(UtilityItem.WIND_CHARGE, 4));
        setButton(24, createShopItemButton(UtilityItem.BOW, 1));

        // Row 4
        setButton(30, createShopItemButton(UtilityItem.SOUL_SAND, 16));
        setButton(33, createShopItemButton(UtilityItem.ARROWS, 5));
    }
}

