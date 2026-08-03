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

    // Separator column base slot
    private static final int SEPARATOR_COLUMN_BASE_SLOT = 13;

    public WeaponsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.WEAPONS);
    }

    @Override
    protected void populateItems() {
        // Sword progression
        boolean hasIronSword = hasItem(Material.IRON_SWORD);
        boolean hasDiamondSword = hasItem(Material.DIAMOND_SWORD);
        if (hasDiamondSword) {
            setButton(11, createPurchasableButtonMaxed(WeaponItem.DIAMOND_SWORD, true));
        } else if (hasIronSword) {
            setButton(11, createPurchasableButtonMaxed(WeaponItem.DIAMOND_SWORD, false));
        } else {
            setButton(11, createPurchasableButtonMaxed(WeaponItem.IRON_SWORD, false));
        }

        // Axe progression
        boolean hasIronAxe = hasItem(Material.IRON_AXE);
        boolean hasDiamondAxe = hasItem(Material.DIAMOND_AXE);
        if (hasDiamondAxe) {
            setButton(12, createPurchasableButtonMaxed(WeaponItem.DIAMOND_AXE, true));
        } else if (hasIronAxe) {
            setButton(12, createPurchasableButtonMaxed(WeaponItem.DIAMOND_AXE, false));
        } else {
            setButton(12, createPurchasableButtonMaxed(WeaponItem.IRON_AXE, false));
        }

        // Separator
        for (int i = 0; i < 2; i++) {
            setItem(SEPARATOR_COLUMN_BASE_SLOT + i * 18, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }
    }
}

