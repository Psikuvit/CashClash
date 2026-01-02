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

    public EnchantsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.ENCHANTS);
    }

    @Override
    protected void populateItems() {
        int slot = 10;
        for (EnchantEntry ee : EnchantEntry.values()) {
            if (slot >= 44) break;

            CashClashPlayer ccp = getCashClashPlayer();
            int currentLevel = ccp != null ? ccp.getOwnedEnchantLevel(ee) : 0;
            int nextLevel = currentLevel + 1;

            final EnchantEntry enchant = ee;
            final int level = nextLevel;

            if (nextLevel > ee.getMaxLevel()) {
                setButton(slot++, GuiButton.of(GuiItemUtils.createMaxedEnchant(ee)));
            } else {
                long price = ee.getPriceForLevel(nextLevel);
                ItemStack enchantItem = GuiItemUtils.createEnchantItem(ee, nextLevel, price);
                setButton(slot++, GuiButton.of(enchantItem).onClick(p -> handleEnchantPurchase(enchant, level)));
            }

            // Skip to next row at certain slots
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
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

        ShopService.getInstance().purchase(viewer, price);
        ccp.addOwnedEnchant(ee, nextLevel);

        ItemUtils.applyEnchantToBestItem(viewer, ee, nextLevel);
        Messages.send(viewer, "<green>Purchased " + ee.getDisplayName() + " " + nextLevel + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(viewer, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        refresh();
    }
}

