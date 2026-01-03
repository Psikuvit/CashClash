package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.FoodItem;
import org.bukkit.entity.Player;

/**
 * Shop category GUI for food items.
 */
public class FoodCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_food";

    public FoodCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.FOOD);
    }

    @Override
    protected void populateItems() {
        // Standard food
        setButton(19, createPurchasableButton(FoodItem.BREAD, 4));
        setButton(20, createPurchasableButton(FoodItem.COOKED_MUTTON, 4));
        setButton(21, createPurchasableButton(FoodItem.STEAK, 4));
        setButton(22, createPurchasableButton(FoodItem.PORKCHOP, 4));
        setButton(23, createPurchasableButton(FoodItem.GOLDEN_CARROT, 4));
        setButton(25, createPurchasableButton(FoodItem.GOLDEN_APPLE, 1));

        // Special food
        setButton(28, createPurchasableButton(FoodItem.SPEED_CARROT, 2));
        setButton(29, createPurchasableButton(FoodItem.GOLDEN_CHICKEN, 2));
        setButton(30, createPurchasableButton(FoodItem.COOKIE_OF_LIFE, 2));
        setButton(31, createPurchasableButton(FoodItem.SUNSCREEN, 2));
        setButton(32, createPurchasableButton(FoodItem.CAN_OF_SPINACH, 2));
        setButton(34, createPurchasableButton(FoodItem.ENCHANTED_GOLDEN_APPLE, 1));
    }
}

