package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shop category GUI for enchantments.
 */
public class EnchantsCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_enchants";
    private static final int[] ENCHANT_SLOTS = {
            3, 5, 10,
            16, 21, 23,
            28, 34, 39,
            41
    };

    public EnchantsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.ENCHANTS);
    }

    @Override
    protected void populateItems() {
        for (int i = 0; i < EnchantEntry.values().length; i++) {
            EnchantEntry ee = EnchantEntry.values()[i];
            int slot = ENCHANT_SLOTS[i];

            CashClashPlayer ccp = getCashClashPlayer();
            int currentLevel = ccp != null ? ccp.getOwnedEnchantLevel(ee) : 0;
            int nextLevel = currentLevel + 1;

            final int level = nextLevel;

            if (nextLevel > ee.getMaxLevel()) {
                setButton(slot, GuiButton.of(GuiItemUtils.createMaxedEnchant(ee)));
            } else {
                long price = ee.getPriceForLevel(nextLevel);
                ItemStack enchantItem = GuiItemUtils.createEnchantItem(ee, nextLevel, price);
                setButton(slot, GuiButton.of(enchantItem).onClick(p -> handleEnchantPurchase(ee, level)));
            }
        }
    }

    private void handleEnchantPurchase(EnchantEntry ee, int nextLevel) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You must be in a game to shop.</red>");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        int currentLevel = ccp.getOwnedEnchantLevel(ee);
        if (nextLevel != currentLevel + 1) {
            // Level mismatch - refresh GUI
            refresh();
            return;
        }

        if (nextLevel > ee.getMaxLevel()) {
            Messages.send(viewer, "<yellow>You already have the maximum level of this enchant!</yellow>");
            return;
        }

        long price = ee.getPriceForLevel(nextLevel);
        if (!ShopService.getInstance().canAfford(viewer, price)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().deductCoins(viewer, price);
        ccp.setOwnedEnchantLevel(ee, nextLevel);

        ItemUtils.applyEnchantToBestItem(viewer, ee, nextLevel);
        Messages.send(viewer, "<green>Purchased " + ee.getDisplayName() + " " + nextLevel + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(viewer, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        refresh();
    }
}

