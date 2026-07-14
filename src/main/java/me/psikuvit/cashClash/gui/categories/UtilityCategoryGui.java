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
        setButton(12, createPurchasableButton(UtilityItem.WATER_BUCKET, 1));
        setButton(13, createPurchasableButton(UtilityItem.LAVA_BUCKET, 1));
        setButton(14, createPurchasableButton(UtilityItem.LEAVES, 16));


        // Row 3
        setButton(21, createPurchasableButton(UtilityItem.COBWEB, 1));
        setButton(22, createPurchasableButton(UtilityItem.ARROWS, 5));
        setButton(23, createPurchasableButton(UtilityItem.TOTEM, 1));


        // Row 4
        setButton(31, createPurchasableButton(UtilityItem.SPECTRAL_ARROW, 5));
    }
}