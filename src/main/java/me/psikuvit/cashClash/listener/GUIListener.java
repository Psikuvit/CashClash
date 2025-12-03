package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.gui.ArenaSelectionHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * Handles GUI interactions
 */
public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        // Check if it's the arena selection GUI via holder
        if (holder instanceof ArenaSelectionHolder) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }

            int slot = event.getSlot();

            if (slot == 22) {
                player.closeInventory();
                return;
            }

            if (slot >= 10 && slot <= 14) {
                int arenaNumber = slot - 9;
                ArenaSelectionGUI.handleArenaClick(player, arenaNumber);
            }
            return;
        }

        if (holder instanceof ShopHolder sh && sh.type().startsWith("categories")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            var meta = clicked.getItemMeta();
            Component nameComp = meta.displayName();

            for (ShopCategory c : ShopCategory.values()) {
                if (Messages.parse("<yellow>" + c.getDisplayName() + "</yellow>").equals(nameComp)) {
                    ShopGUI.openCategoryItems(player, c);
                    return;
                }
            }
            // cancel button at slot 8
            if (event.getSlot() == 8) {
                player.closeInventory();
            }
            return;
        }

        // Shop category items
        if (holder instanceof ShopHolder sh2 && sh2.type().startsWith("category:")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            var itemMeta = clicked.getItemMeta();
            Component itemComp = itemMeta.displayName();

            // Cancel button in items view
            if (event.getSlot() == 45) {
                player.closeInventory();
                return;
            }

            // Undo purchase
            if (event.getSlot() == 49) {
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
                return;
            }

            String type = sh2.type();
            if (type.startsWith("category:")) {
                String cat = type.substring("category:".length());
                if (cat.equalsIgnoreCase(ShopCategory.ENCHANTS.getDisplayName())) {
                    for (EnchantEntry ee : EnchantEntry.values()) {
                        for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
                            Component expected = Messages.parse("<yellow>" + ee.getDisplayName() + " " + lvl + "</yellow>");
                            if (expected.equals(itemComp)) {

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

                                applyEnchantToBestItem(player, ee, lvl);
                                Messages.send(player, "<green>Purchased enchant " + ee.getDisplayName() + " " + lvl + " for $" + price + "</green>");
                                return;
                            }
                        }
                    }
                }
            }

            // Find ShopItem by name
            for (ShopItem si : ShopItem.values()) {
                Component expectedComp = Messages.parse("<yellow>" + si.name().replace('_',' ') + "</yellow>");
                if (expectedComp.equals(itemComp)) {

                    GameSession sess = GameManager.getInstance().getPlayerSession(player);
                    if (sess == null) {
                        Messages.send(player, "<red>You must be in a game to shop.</red>");
                        player.closeInventory();
                        return;
                    }

                    CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
                    if (ccp == null) return;

                    long price = si.getPrice();
                    if (!ccp.canAfford(price)) {
                        Messages.send(player, "<red>Not enough coins to buy " + si.name() + " ($" + price + ")</red>");
                        return;
                    }
                    ccp.deductCoins(price);

                    ItemStack given = new ItemStack(si.getMaterial(), 1);
                    var meta = given.getItemMeta();

                    NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, si.name());

                    given.setItemMeta(meta);
                    player.getInventory().addItem(given);
                    ccp.addPurchase(new PurchaseRecord(si, 1, price));

                    applyOwnedEnchantsAfterPurchase(player, si);

                    Messages.send(player, "<green>Purchased " + si.name() + " for $" + price + "</green>");
                    return;
                }
            }
        }
    }

    private void applyEnchantToBestItem(Player player, EnchantEntry ee, int lvl) {
        ItemStack best = null;
        int bestSlot = -1;
        int eligibleCount = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack is = player.getInventory().getItem(i);

            if (is == null) continue;
            if (!ee.getApplicableMaterials().contains(is.getType())) continue;
            eligibleCount++;

            if (best == null) {
                best = is;
                bestSlot = i;
                continue;
            }
            if (rankMaterial(is.getType()) > rankMaterial(best.getType())) {
                best = is;
                bestSlot = i;
            }
        }
        if (best != null) {
            var meta = best.getItemMeta();
            if (meta != null) {
                meta.addEnchant(ee.getEnchantment(), lvl, true);
                best.setItemMeta(meta);
                player.getInventory().setItem(bestSlot, best);
                if (eligibleCount > 1) {
                    Messages.send(player, "<yellow>Multiple eligible items found; enchant auto-applied to the strongest matching item.</yellow>");
                }
            }
        } else {
            Messages.send(player, "<red>No eligible item found in your inventory to apply that enchant. It will be saved and applied to future purchases when appropriate.</red>");
        }
    }

    private void applyOwnedEnchantsAfterPurchase(Player player, ShopItem si) {
        if (si.getCategory() == ShopCategory.UTILITY && (si.getMaterial() == Material.BOW || si.getMaterial() == Material.CROSSBOW)) return;

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        var ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        for (var e : ccp.getOwnedEnchants().entrySet()) {
            EnchantEntry ee = e.getKey();
            int lvl = e.getValue();
            if (ee.getApplicableMaterials().contains(si.getMaterial())) {
                applyEnchantToBestItem(player, ee, lvl);
            }
        }
    }

    private int rankMaterial(Material m) {
        // basic ranking: Netherite(5), Diamond(4), Iron(3), Stone(2), Wood/others(1)
        if (m.name().contains("NETHERITE")) return 5;
        if (m.name().contains("DIAMOND")) return 4;
        if (m.name().contains("IRON")) return 3;
        if (m.name().contains("STONE")) return 2;
        return 1;
    }
}
