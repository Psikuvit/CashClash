package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

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

        // Ranged weapons
        setButton(14, createPurchasableButton(UtilityItem.CROSSBOW, 1));
        setButton(15, createPurchasableButton(UtilityItem.BOW, 1));

        // Signature weapons (unique ownership)
        setButton(30, createSignatureWeaponButton(WeaponItem.SOUL_KATANA));
        setButton(32, createSignatureWeaponButton(WeaponItem.CASH_BLASTER));

        // Separator
        for (int i = 0; i < 2; i++) {
            setItem(SEPARATOR_COLUMN_BASE_SLOT + i * 18, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }
    }

    /**
     * Creates a button for a unique weapon that the player can only own once.
     */
    private GuiButton createSignatureWeaponButton(WeaponItem item) {
        ItemStack itemStack = ItemFactory.getInstance().createGuiItem(viewer, item, 1);
        return GuiButton.of(itemStack).onClick(p -> {
            for (ItemStack is : p.getInventory().getContents()) {
                if (is != null && PDCDetection.getWeapon(is) == item) {
                    Messages.send(p, "customitem.already-owned", "item_name", item.getDisplayName());
                    SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
            handlePurchasableClick(item, ClickType.LEFT);
        });
    }
}
