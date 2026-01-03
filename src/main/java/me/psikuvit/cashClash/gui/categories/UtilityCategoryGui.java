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
        setButton(11, createPurchasableButton(UtilityItem.LAVA_BUCKET, 1));
        setButton(12, createPurchasableButton(UtilityItem.FISHING_ROD, 1));
        setButton(13, createPurchasableButton(UtilityItem.COBWEB, 4));
        setButton(15, createPurchasableButton(UtilityItem.CROSSBOW, 1));

        // Row 3
        setButton(20, createPurchasableButton(UtilityItem.LEAVES, 16));
        setButton(21, createPurchasableButton(UtilityItem.WATER_BUCKET, 1));
        setButton(22, createPurchasableButton(UtilityItem.WIND_CHARGE, 4));
        setButton(24, createPurchasableButton(UtilityItem.BOW, 1));

        // Row 4
        setButton(30, createPurchasableButton(UtilityItem.SOUL_SAND, 16));
        setButton(33, createPurchasableButton(UtilityItem.ARROWS, 5));
    }
}

