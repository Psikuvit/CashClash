package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        setButton(20, createCustomItemButton(CustomItem.GRENADE));
        setButton(21, createCustomItemButton(CustomItem.SMOKE_CLOUD_GRENADE));
        setButton(22, createCustomItemButton(CustomItem.BAG_OF_POTATOES));
        setButton(23, createCustomItemButton(CustomItem.CASH_BLASTER));
        setButton(24, createCustomItemButton(CustomItem.BOUNCE_PAD));

        // Row 4
        setButton(29, createCustomItemButton(CustomItem.BOOMBOX));
        setButton(30, createCustomItemButton(CustomItem.MEDIC_POUCH));
        setButton(31, createCustomItemButton(CustomItem.INVIS_CLOAK));
        setButton(32, createCustomItemButton(CustomItem.TABLET_OF_HACKING));
        setButton(33, createCustomItemButton(CustomItem.RESPAWN_ANCHOR));
    }

    private GuiButton createCustomItemButton(CustomItem item) {
        ItemStack itemStack = ItemFactory.getInstance().getGuiFactory().createCustomItemIcon(item);
        return GuiButton.of(itemStack).onClick(p -> handleCustomItemPurchase(item));
    }

    private void handleCustomItemPurchase(CustomItem type) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "shop.must-be-in-game");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        // Check if trying to buy more than 1 invisibility cloak per round
        if (type == CustomItem.INVIS_CLOAK) {
            // Check if player already has an invis cloak in inventory
            int cloakCount = 0;
            for (ItemStack item : viewer.getInventory().getContents()) {
                if (item != null && PDCDetection.getCustomItem(item) == CustomItem.INVIS_CLOAK) {
                    cloakCount++;
                }
            }
            if (cloakCount > 0) {
                Messages.send(viewer, "customitem.max-one-cloak");
                SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        long price = type.getPrice();
        if (!ShopService.getInstance().canAfford(viewer, price)) {
            Messages.send(viewer, "shop.not-enough-coins", "cost", String.format("%,d", price));
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack customItem = ItemFactory.getInstance().createCustomItem(type, viewer);
        ccp.addPurchase(new PurchaseRecord(type, 1, price, sess.getCurrentRound()));

        ShopService.getInstance().deductCoins(viewer, price);
        viewer.getInventory().addItem(customItem);

        Messages.send(viewer, "shop.purchased", "item_name", type.getDisplayName(), "price", String.format("%,d", price));
        SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        refresh();
    }
}
