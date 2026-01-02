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
        setButton(19, createShopItemButton(FoodItem.BREAD, 4));
        setButton(20, createShopItemButton(FoodItem.COOKED_MUTTON, 4));
        setButton(21, createShopItemButton(FoodItem.STEAK, 4));
        setButton(22, createShopItemButton(FoodItem.PORKCHOP, 4));
        setButton(23, createShopItemButton(FoodItem.GOLDEN_CARROT, 4));
        setButton(25, createShopItemButton(FoodItem.GOLDEN_APPLE, 1));

        // Special food
        setButton(28, createShopItemButton(FoodItem.SPEED_CARROT, 2));
        setButton(29, createShopItemButton(FoodItem.GOLDEN_CHICKEN, 2));
        setButton(30, createShopItemButton(FoodItem.COOKIE_OF_LIFE, 2));
        setButton(31, createShopItemButton(FoodItem.SUNSCREEN, 2));
        setButton(32, createShopItemButton(FoodItem.CAN_OF_SPINACH, 2));
        setButton(34, createShopItemButton(FoodItem.ENCHANTED_GOLDEN_APPLE, 1));
    }
}

