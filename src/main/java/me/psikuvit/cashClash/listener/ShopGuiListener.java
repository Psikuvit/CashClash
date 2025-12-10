package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.gui.GuiType;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.ItemSelectionUtils;
import me.psikuvit.cashClash.util.ItemUtils;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SoundUtils;
import me.psikuvit.cashClash.items.CustomArmor;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Listener dedicated to shop GUI interactions.
 */
public class ShopGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolder sh)) return;

        GuiType type = sh.getType();
        if (type == null) return;

        event.setCancelled(true);

        if (type.startsWith("categories")) {
            handleShopCategories(event, player); // removed unused 'sh' param
            return;
        }

        if (type == GuiType.CATEGORY) {
            handleCategoryItems(event, player, sh);
        }
    }

    private void handleShopCategories(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        var meta = clicked.getItemMeta();
        Component nameComp = meta.displayName();

        for (ShopCategory c : ShopCategory.values()) {
            Component expected = Messages.parse("<yellow>" + c.getDisplayName() + "</yellow>");
            if (expected.equals(nameComp)) {
                ShopGUI.openCategoryItems(player, c);
                return;
            }
        }

        if (event.getSlot() == 8) {
            player.closeInventory();
        }
    }

    private void handleCategoryItems(InventoryClickEvent event, Player player, ShopHolder sh) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta itemMeta = clicked.getItemMeta();
        String pdcValue = itemMeta.getPersistentDataContainer().get(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING);

        if (event.getSlot() == 45) {
            ShopGUI.openMain(player);
            return;
        }

        if (event.getSlot() == 49) {
            handleUndoPurchase(player);
            return;
        }

        if (sh.getCategory() == ShopCategory.ENCHANTS) {
            handleEnchantPurchase(player, pdcValue);
        } else {
            handleShopItemClick(player, pdcValue);
        }
    }

    private void handleUndoPurchase(Player player) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        PurchaseRecord rec = ccp.popLastPurchase();
        if (rec == null) {
            Messages.send(player, "<red>No purchase to undo.</red>");
            return;
        }

        // refund
        ccp.addCoins(rec.price());

        boolean removed = false;

        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;

            ItemMeta meta = is.getItemMeta();
            if (meta == null) continue;

            String val = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
            if (val != null && val.equals(rec.item().name())) {
                int qty = is.getAmount();

                if (qty > 1) is.setAmount(qty - 1);
                else player.getInventory().remove(is);

                removed = true;
                break;
            }
        }
        Messages.send(player, "<green>Purchase undone. Refunded $" + rec.price() + (removed ? "" : " (could not find item to remove)") + "</green>");
    }

    private void handleEnchantPurchase(Player player, String pdcValue) {
        EnchantEntry ee = EnchantEntry.valueOf(pdcValue);
        for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
            GameSession sess = GameManager.getInstance().getPlayerSession(player);
            if (sess == null) {
                Messages.send(player, "<red>You must be in a game to shop.</red>");
                player.closeInventory();
                return;
            }

            CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
            if (ccp == null) return;

            long price = ee.getPriceForLevel(lvl);
            if (!ccp.canAfford(price)) {
                Messages.send(player, "<red>Not enough coins to buy enchant.</red>");
                return;
            }

            ccp.deductCoins(price);
            ccp.addOwnedEnchant(ee, lvl);

            ItemUtils.applyEnchantToBestItem(player, ee, lvl);
            Messages.send(player, "<green>Purchased enchant " + ee.getDisplayName() + " " + lvl + " for $" + price + "</green>");
        }
    }


    private void handleShopItemClick(Player player, String pdcValue) {
        ShopItem si = ShopItem.valueOf(pdcValue);

        // validate session and player data
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

            long price = si.getPrice();
            if (!canAffordAndDeduct(ccp, price, player)) return;

        if (si == ShopItem.UPGRADE_TO_NETHERITE) {
            handleUpgradeToNetherite(player, ccp, si, price);
            return;
        }

            // build the item that will be given
            ItemStack given = createTaggedItem(si);

        if (si == ShopItem.DEATHMAULER_OUTFIT || si == ShopItem.DRAGON_SET) {
            giveSpecialSet(player, si, ccp, price);
            return;
        }

        if (!ensureDiamondPrerequisite(si, player, ccp)) return;
        giveItemToPlayer(ccp, si, given);
        ccp.deductCoins(price);
        return true;
    }

    private void handleUpgradeToNetherite(Player player, CashClashPlayer ccp, ShopItem si, long price) {
        boolean upgraded = ItemUtils.upgradeBestDiamondToNetherite(player);
        if (upgraded) {
            ccp.addPurchase(new PurchaseRecord(si, 1, price));
            Messages.send(player, "<green>Upgraded your best diamond item to netherite.</green>");
            SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            Messages.send(player, "<yellow>No eligible diamond item found to upgrade.</yellow>");
            ccp.addCoins(price);
        }
    }

    private ItemStack createTaggedItem(ShopItem si) {
        return ItemUtils.createTaggedItem(si);
    }

    private void giveSpecialSet(Player player, ShopItem si, CashClashPlayer ccp, long price) {
        if (si == ShopItem.DEATHMAULER_OUTFIT) ItemUtils.giveCustomArmorSet(player, CustomArmor.DEATHMAULER_OUTFIT);
        else if (si == ShopItem.DRAGON_SET) ItemUtils.giveCustomArmorSet(player, CustomArmor.DRAGON_SET);

        ccp.addPurchase(new PurchaseRecord(si, 1, price));
        Messages.send(player, "<green>Purchased " + si.name().replace('_', ' ') + " for $" + price + "</green>");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private boolean ensureDiamondPrerequisite(ShopItem si, Player player, CashClashPlayer ccp) {
        if (!si.getMaterial().name().contains("DIAMOND")) return true;
        String ironName = si.getMaterial().name().replace("DIAMOND", "IRON");

        boolean hasIron = false;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;
            if (is.getType().name().equals(ironName)) { hasIron = true; break; }
        }

        if (!hasIron) {
            Messages.send(player, "<red>You must own the iron equivalent before buying diamond " + si.name().replace('_', ' ') + "</red>");
            ccp.addCoins(si.getPrice());
        }

        return hasIron;
    }

    private void giveItemToPlayer(Player player, ShopItem si, ItemStack item, CashClashPlayer ccp) {
        if (si.getCategory() == ShopCategory.ARMOR) {
            ItemUtils.equipArmorOrReplace(player, item);
        } else if (ItemSelectionUtils.isToolOrWeapon(si.getMaterial())) {
            ItemUtils.replaceBestMatchingTool(player, item);
        } else {
            player.getInventory().addItem(item);
        }

        ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice()));
        // apply owned enchants centrally
        ItemUtils.applyOwnedEnchantsAfterPurchase(player, si);
        Messages.send(player, "<green>Purchased " + si.name().replace('_', ' ') + " for $" + si.getPrice() + "</green>");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
}
