package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.ItemSelectionUtils;
import me.psikuvit.cashClash.util.ItemUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

        String type = sh.type();
        if (type == null) return;

        if (type.startsWith("categories") || type.startsWith("category:")) {
            event.setCancelled(true);
        }

        if (type.startsWith("categories")) {
            handleShopCategories(event, player); // removed unused 'sh' param
            return;
        }

        if (type.startsWith("category:")) {
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

        var itemMeta = clicked.getItemMeta();
        Component itemComp = itemMeta.displayName();

        if (event.getSlot() == 45) {
            player.closeInventory();
            return;
        }

        if (event.getSlot() == 49) {
            handleUndoPurchase(player);
            return;
        }

        String type = sh.type();
        if (type != null && type.startsWith("category:")) {
            String cat = type.substring("category:".length());
            if (cat.equalsIgnoreCase(ShopCategory.ENCHANTS.getDisplayName())) {
                if (handleEnchantPurchase(player, itemComp)) return;
            }
        }

        handleShopItemClick(player, itemComp);
    }

    private void handleUndoPurchase(Player player) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        var rec = ccp.popLastPurchase();
        if (rec == null) {
            Messages.send(player, "<red>No purchase to undo.</red>");
            return;
        }

        // refund
        ccp.addCoins(rec.price());

        NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
        boolean removed = false;

        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;

            var m = is.getItemMeta();
            if (m == null) continue;

            var val = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
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

    private boolean handleEnchantPurchase(Player player, Component itemComp) {
        for (EnchantEntry ee : EnchantEntry.values()) {
            for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
                Component expected = Messages.parse("<yellow>" + ee.getDisplayName() + " " + lvl + "</yellow>");
                if (expected.equals(itemComp)) {

                    GameSession sess = GameManager.getInstance().getPlayerSession(player);
                    if (sess == null) {
                        Messages.send(player, "<red>You must be in a game to shop.</red>");
                        player.closeInventory();
                        return true;
                    }

                    CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
                    if (ccp == null) return true;

                    long price = ee.getPriceForLevel(lvl);
                    if (!ccp.canAfford(price)) {
                        Messages.send(player, "<red>Not enough coins to buy enchant.</red>");
                        return true;
                    }

                    ccp.deductCoins(price);
                    ccp.addOwnedEnchant(ee, lvl);

                    ItemUtils.applyEnchantToBestItem(player, ee, lvl);
                    Messages.send(player, "<green>Purchased enchant " + ee.getDisplayName() + " " + lvl + " for $" + price + "</green>");
                    return true;
                }
            }
        }
        return false;
    }

    private void handleShopItemClick(Player player, Component itemComp) {
        for (ShopItem si : ShopItem.values()) {
            Component expectedComp = Messages.parse("<yellow>" + si.name().replace('_', ' ') + "</yellow>");
            if (!expectedComp.equals(itemComp)) continue;

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

            // handle immediate special cases
            if (si == ShopItem.UPGRADE_TO_NETHERITE) {
                handleUpgradeToNetherite(player, ccp, si, price);
                return;
            }

            // build the item that will be given
            ItemStack given = createTaggedItem(si);

            // special set purchases
            if (si == ShopItem.DEATHMAULER_OUTFIT || si == ShopItem.DRAGON_SET) {
                giveSpecialSet(player, si, ccp, price);
                return;
            }

            // diamond prereq
            if (!ensureDiamondPrerequisite(si, player, ccp)) return;

            // give item (equip or add)
            giveItemToPlayer(player, si, given, ccp);
            return;
        }
    }

    private boolean canAffordAndDeduct(CashClashPlayer ccp, long price, Player player) {
        if (!ccp.canAfford(price)) {
            Messages.send(player, "<red>Not enough coins to buy (cost: $" + price + ")</red>");
            return false;
        }
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
