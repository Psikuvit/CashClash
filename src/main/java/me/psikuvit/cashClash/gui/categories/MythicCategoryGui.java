package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Utility class for handling mythic item purchases in the shop.
 * Mythic items are shown in the main shop GUI, not in a separate category.
 */
public final class MythicCategoryGui {

    private MythicCategoryGui() {
        // Utility class
    }

    /**
     * Handle the purchase of a mythic item.
     *
     * @param player   The player purchasing
     * @param mythic   The mythic item to purchase
     * @param parentGui The parent GUI to refresh after purchase
     */
    public static void handleMythicPurchase(Player player, MythicItem mythic, AbstractGui parentGui) {
        GameSession sess = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "shop.must-be-in-game");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        UUID playerUuid = player.getUniqueId();

        if (CashClashPlugin.getInstance().getMythicItemManager().isUnavailable(sess, mythic)) {
            Messages.send(player, "shop.mythic-unavailable");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (CashClashPlugin.getInstance().getMythicItemManager().isMythicPurchased(sess, mythic)) {
            UUID ownerUuid = CashClashPlugin.getInstance().getMythicItemManager().getMythicOwner(sess, mythic);
            Messages.send(player, "shop.mythic-already-purchased", "owner_name",
                ownerUuid == null ? "Someone" : String.valueOf(Bukkit.getOfflinePlayer(ownerUuid).getName()));
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (CashClashPlugin.getInstance().getMythicItemManager().hasPlayerPurchasedMythic(sess, playerUuid)) {
            MythicItem owned = CashClashPlugin.getInstance().getMythicItemManager().getPlayerMythic(sess, playerUuid);
            Messages.send(player, "shop.mythic-already-owned", "mythic_name", owned.getDisplayName());
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long price = mythic.getPrice();
        if (!CashClashPlugin.getInstance().getShopService().canAfford(player, price)) {
            Messages.send(player, "shop.not-enough-coins", "cost", String.format("%,d", price));
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        CashClashPlugin.getInstance().getShopService().processPurchase(player, mythic, 1, price);
        CashClashPlugin.getInstance().getMythicItemManager().registerMythicPurchase(sess, playerUuid, mythic);

        ItemStack mythicItem = CashClashPlugin.getInstance().getMythicItemManager().createMythicItem(mythic, player);
        ItemUtils.replaceBestMatchingTool(player, mythicItem);

        if (mythic == MythicItem.WIND_BOW) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, 20));
        }

        Messages.send(player, "bonus.announce-spacer");
        Messages.send(player, "shop.mythic-acquired-title");
        Messages.send(player, "shop.mythic-acquired-name", "mythic_name", mythic.getDisplayName());
        Messages.send(player, "shop.mythic-acquired-cost", "price", String.format("%,d", price));
        Messages.send(player, "bonus.announce-spacer");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // Refresh the parent GUI
        if (parentGui != null) {
            parentGui.open();
        } else {
            ShopGUI.openMain(player);
        }
    }
}

