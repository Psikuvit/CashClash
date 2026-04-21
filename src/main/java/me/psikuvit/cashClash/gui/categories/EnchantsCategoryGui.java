package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
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
                setButton(slot, GuiButton.of(ItemFactory.getInstance().getGuiFactory().createMaxedEnchant(ee)));
            } else {
                long price = ee.getPriceForLevel(nextLevel);
                ItemStack enchantItem = ItemFactory.getInstance().getGuiFactory().createEnchantItem(ee, nextLevel, price);
                setButton(slot, GuiButton.of(enchantItem).onClick(p -> handleEnchantPurchase(ee, level)));
            }
        }
    }

    private void handleEnchantPurchase(EnchantEntry ee, int nextLevel) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "shop.must-be-in-game");
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
            Messages.send(viewer, "shop.max-enchant-level");
            return;
        }

        long price = ee.getPriceForLevel(nextLevel);
        if (!ShopService.getInstance().canAfford(viewer, price)) {
            Messages.send(viewer, "shop.not-enough-coins", "cost", String.format("%,d", price));
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deathmauler restriction: Cannot buy both protection types
        CustomArmorManager armorManager = CustomArmorManager.getInstance();
        if (armorManager.hasDeathmaulerSet(viewer)) {
            if (ee == EnchantEntry.PROTECTION && ccp.getOwnedEnchantLevel(EnchantEntry.PROJECTILE_PROTECTION) > 0) {
                Messages.send(viewer, "shop.deathmauler-has-projectile");
                Messages.send(viewer, "shop.deathmauler-protection-choice");
                SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            if (ee == EnchantEntry.PROJECTILE_PROTECTION && ccp.getOwnedEnchantLevel(EnchantEntry.PROTECTION) > 0) {
                Messages.send(viewer, "shop.deathmauler-has-protection");
                Messages.send(viewer, "shop.deathmauler-protection-choice");
                SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        if (ItemUtils.applyEnchant(viewer, ee, nextLevel)) {
            ShopService.getInstance().deductCoins(viewer, price);
            ccp.setOwnedEnchantLevel(ee, nextLevel);
            Messages.send(viewer, "shop.enchant-purchased",
                    "enchant_name", ee.getDisplayName(), "level", String.valueOf(nextLevel), "price", String.format("%,d", price));
            SoundUtils.play(viewer, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            refresh();
        }
    }
}

