package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Shop category GUI for weapons.
 */
public class WeaponsCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_weapons";

    public WeaponsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.WEAPONS);
    }

    @Override
    protected void populateItems() {
        // Sword progression
        boolean hasIronSword = hasItem(Material.IRON_SWORD);
        boolean hasDiamondSword = hasItem(Material.DIAMOND_SWORD);
        if (hasDiamondSword) {
            setButton(23, createPurchasableButtonMaxed(WeaponItem.DIAMOND_SWORD, true));
        } else if (hasIronSword) {
            setButton(23, createPurchasableButtonMaxed(WeaponItem.DIAMOND_SWORD, false));
        } else {
            setButton(23, createPurchasableButtonMaxed(WeaponItem.IRON_SWORD, false));
        }

        // Axe progression
        boolean hasIronAxe = hasItem(Material.IRON_AXE);
        boolean hasDiamondAxe = hasItem(Material.DIAMOND_AXE);
        if (hasDiamondAxe) {
            setButton(21, createPurchasableButtonMaxed(WeaponItem.DIAMOND_AXE, true));
        } else if (hasIronAxe) {
            setButton(21, createPurchasableButtonMaxed(WeaponItem.DIAMOND_AXE, false));
        } else {
            setButton(21, createPurchasableButtonMaxed(WeaponItem.IRON_AXE, false));
        }
    }
}

