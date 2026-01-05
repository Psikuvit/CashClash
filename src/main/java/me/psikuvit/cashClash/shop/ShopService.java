package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Service class for handling shop-related operations.
 */
public class ShopService {

    private static ShopService instance;

    private ShopService() {
    }

    public static ShopService getInstance() {
        if (instance == null) {
            instance = new ShopService();
        }
        return instance;
    }

    public long calculateTotalPrice(Purchasable item, int quantity) {
        return item.getPrice() * Math.max(1, quantity);
    }

    public void processPurchase(Player player, Purchasable item, int quantity, long totalPrice) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp == null) return;

        deductCoins(player, totalPrice);
        giveItemToPlayer(player, ccp, item, quantity, totalPrice);
    }


    public void refund(Player player, long amount) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.addCoins(amount);
            Messages.send(player, "<green>Refunded $" + String.format("%,d", amount) + " to your balance.</green>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }
    }

    public void processRefund(Player player, PurchaseRecord record) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp == null) return;

        refund(player, record.price());
        boolean removed = ItemUtils.removeItemFromPlayer(player, record.item().name(), record.quantity());

        if (record.replacedItem() != null) {
            player.getInventory().addItem(record.replacedItem());
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", record.price()) + " and restored your previous item.</green>");
        } else {
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", record.price()) +
                    (removed ? "" : " (could not find item(s) to remove)") + "</green>");
        }

        SoundUtils.play(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
    }

    /**
     * Check if a player can afford a purchase.
     *
     * @param player The player making the purchase.
     * @param cost   The cost of the purchase.
     * @return True if the player can afford the purchase, false otherwise.
     */
    public boolean canAfford(Player player, long cost) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        return ccp != null && ccp.getCoins() >= cost;
    }

    private void giveItemToPlayer(Player player, CashClashPlayer ccp, Purchasable item, int quantity, long totalPrice) {
        int giveQty = Math.max(1, Math.min(quantity, item.getInitialAmount()));

        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        int round = sess != null ? sess.getCurrentRound() : 1;

        if (item instanceof CustomArmorItem customArmor) {
            ItemStack replacedItem = ItemUtils.giveCustomArmorSet(player, customArmor);
            ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));

            ItemUtils.applyOwnedEnchantsAfterPurchase(player, item);
            Messages.send(player, "<green>Purchased " + item.getDisplayName() + " for $" + String.format("%,d", item.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else if (item.getCategory() == ShopCategory.ARMOR) {
            ItemStack armorItem = ItemUtils.createTaggedItem(item);
            ItemStack replacedItem = ItemUtils.equipArmorOrReplace(player, armorItem);

            if (replacedItem != null) {
                player.getInventory().addItem(replacedItem);
            }

            ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));
            ItemUtils.applyOwnedEnchantsAfterPurchase(player, item);

            Messages.send(player, "<green>Purchased " + item.getDisplayName() + " for $" + String.format("%,d", item.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else {
            ItemStack stack = ItemUtils.createTaggedItem(item).clone();
            stack.setAmount(giveQty);
            player.getInventory().addItem(stack);
            ccp.addPurchase(new PurchaseRecord(item, giveQty, totalPrice, round));

            Messages.send(player, "<green>Purchased " + item.getDisplayName() + " x" + giveQty + " for $" + String.format("%,d", totalPrice) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    public void deductCoins(Player player, long cost) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.deductCoins(cost);
            Messages.send(player, "<green>Purchase successful! Deducted $" + String.format("%,d", cost) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private CashClashPlayer getCashClashPlayer(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null ? session.getCashClashPlayer(player.getUniqueId()) : null;
    }
}
