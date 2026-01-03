package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
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
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        UUID playerUuid = player.getUniqueId();

        if (!MythicItemManager.getInstance().isMythicAvailableInSession(sess, mythic)) {
            Messages.send(player, "<red>This mythic is not available in this game!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (MythicItemManager.getInstance().isMythicPurchased(sess, mythic)) {
            UUID ownerUuid = MythicItemManager.getInstance().getMythicOwner(sess, mythic);
            String ownerName = ownerUuid != null ? Bukkit.getOfflinePlayer(ownerUuid).getName() : "Someone";
            Messages.send(player, "<red>This mythic has already been purchased by " + ownerName + "!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (MythicItemManager.getInstance().hasPlayerPurchasedMythic(sess, playerUuid)) {
            MythicItem owned = MythicItemManager.getInstance().getPlayerMythic(sess, playerUuid);
            Messages.send(player, "<red>You already own a mythic (" + owned.getDisplayName() + ")!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long price = mythic.getPrice();
        if (!ShopService.getInstance().canAfford(player, price)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().purchase(player, price);
        MythicItemManager.getInstance().registerMythicPurchase(sess, playerUuid, mythic);

        // BlazeBite gives two crossbows (Glacier + Volcano)
        if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
            ItemStack[] crossbows = MythicItemManager.getInstance().createBlazebiteBundle(player);
            player.getInventory().addItem(crossbows[0]); // Glacier
            player.getInventory().addItem(crossbows[1]); // Volcano
        } else {
            ItemStack mythicItem = MythicItemManager.getInstance().createMythicItem(mythic, player);
            player.getInventory().addItem(mythicItem);
        }

        if (mythic == MythicItem.WIND_BOW) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, 20));
        }

        Messages.send(player, "");
        Messages.send(player, "<dark_purple><bold>✦ MYTHIC ACQUIRED ✦</bold></dark_purple>");
        Messages.send(player, "<light_purple>" + mythic.getDisplayName() + "</light_purple>");
        Messages.send(player, "<dark_gray>-$" + String.format("%,d", price) + "</dark_gray>");
        Messages.send(player, "");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // Refresh the parent GUI
        if (parentGui != null) {
            parentGui.open();
        } else {
            ShopGUI.openMain(player);
        }
    }
}

