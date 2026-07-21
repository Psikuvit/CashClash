package me.psikuvit.cashClash.gui.categories;


import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.CustomWeapon;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;

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

        setButton(14, createPurchasableButton(WeaponItem.CROSSBOW, 1));
        setButton(15, createPurchasableButton(WeaponItem.BOW, 1));
        setButton(30, createWeaponButton(WeaponItem.SOUL_KATANA));
        setButton(32, createWeaponButton(WeaponItem.CASH_BLASTER));

        // Separator
        populateSeparatorColumn();
    }

    // ==================== SEPARATOR COLUMN ====================

    /**
     * Populates the separator column with light blue glass panes.
     */
    private void populateSeparatorColumn() {
        for (int i = 0; i < 2; i++) {
            setItem(SEPARATOR_COLUMN_BASE_SLOT + i * 18, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }
    }

    private void handleWeaponPurchase(WeaponItem type) {

            if (type == WeaponItem.CASH_BLASTER) {

                for (ItemStack item : viewer.getInventory().getContents()) {

                    if (item != null &&
                            PDCDetection.getCustomWeapon(item) == CustomWeapon.CASH_BLASTER) {

                        Messages.send(viewer, "You already own Cash Blaster!");
                        SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                }
            }

            if (type == WeaponItem.SOUL_KATANA) {

                for (ItemStack item : viewer.getInventory().getContents()) {

                    if (item != null &&
                            PDCDetection.getCustomWeapon(item) == CustomWeapon.SOUL_KATANA) {

                        Messages.send(viewer, "You already own Soul Katana!");
                        SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                }
            }
            long price = type.getPrice();

            if (!ShopService.getInstance().canAfford(viewer, price)) {
                Messages.send(viewer, "shop.not-enough-coins", "cost", String.format("%,d", price));
                SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            GameSession sess = getSession();
            if (sess == null) return;

            CashClashPlayer ccp = getCashClashPlayer();
            if (ccp == null) return;

            ShopService.getInstance().deductCoins(viewer, price);

            ItemStack weapon = ItemFactory.getInstance().createGameplayItem(type);

            if (type == WeaponItem.CASH_BLASTER) {
                CustomWeapon.markCashBlaster(weapon);
            }

            if (type == WeaponItem.SOUL_KATANA) {
                CustomWeapon.markSoulKatana(weapon);
            }

            viewer.getInventory().addItem(weapon);

            ccp.addPurchase(new PurchaseRecord(type, 1, price, sess.getCurrentRound()));

            Messages.send(viewer, "shop.purchased",
                    "item_name", type.getDisplayName(),
                    "price", String.format("%,d", price));

            SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

            refresh();
        }



    private GuiButton createWeaponButton(WeaponItem item) {
        ItemStack itemStack = ItemFactory.getInstance()
                .getGuiFactory()
                .createShopItem(viewer, item);

        return GuiButton.of(itemStack)
                .onClick((p, clickType) -> handleWeaponPurchase(item));
    }

    }
