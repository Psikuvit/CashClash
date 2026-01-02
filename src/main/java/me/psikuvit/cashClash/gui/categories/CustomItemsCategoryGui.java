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
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
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
        ItemStack itemStack = GuiItemUtils.createCustomItemIcon(item);
        return GuiButton.of(itemStack).onClick(p -> handleCustomItemPurchase(item));
    }

    private void handleCustomItemPurchase(CustomItem type) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You must be in a game to shop.</red>");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        long price = type.getPrice();
        if (!ShopService.getInstance().canAfford(viewer, price)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack customItem = ItemUtils.createCustomItem(type, viewer);

        ShopService.getInstance().purchase(viewer, price);
        viewer.getInventory().addItem(customItem);

        int round = sess.getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(type, 1, price, round));

        Messages.send(viewer, "<green>Purchased " + type.getDisplayName() + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        refresh();
    }
}

