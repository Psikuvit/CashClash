package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
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
import me.psikuvit.cashClash.util.ItemUtils;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;

import java.util.logging.Level;

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

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        if (type == GuiType.MAIN) {
            handleShopCategories(event, player);
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

        // Check for cancel button
        if (event.getSlot() == 45) {
            player.closeInventory();
            return;
        }

        // Check each category
        for (ShopCategory c : ShopCategory.values()) {
            Component expected = Messages.parse("<yellow>" + c.getDisplayName() + "</yellow>");
            if (expected.equals(nameComp)) {
                ShopGUI.openCategoryItems(player, c);
                return;
            }
        }
    }

    private void handleCategoryItems(InventoryClickEvent event, Player player, ShopHolder sh) {
         ItemStack clicked = event.getCurrentItem();
         if (clicked == null || !clicked.hasItemMeta()) return;

         ItemMeta itemMeta = clicked.getItemMeta();

        // Check for cancel button (slot 45) - go back to main menu
        if (event.getSlot() == 45) {
            ShopGUI.openMain(player);
            return;
        }

        // Check for undo button (slot 49)
        if (event.getSlot() == 49) {
            handleUndoPurchase(player, sh.getCategory());
            return;
        }

        Component displayName = itemMeta.displayName();
        if (displayName != null) {
            String plainName = Messages.parseToLegacy(displayName);
            if (plainName.contains("(Max)") || plainName.contains("(Owned)")) {
                Messages.send(player, "<yellow>You already have the maximum tier of this item!</yellow>");
                SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        String pdcValue = itemMeta.getPersistentDataContainer().get(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING);
        if (pdcValue == null) return;

        ShopCategory category = sh.getCategory();
        ShopItem si;
        try {
            si = ShopItem.valueOf(pdcValue);
        } catch (IllegalArgumentException ex) {
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Unknown shop category: " + pdcValue, ex);
            return;
        }
        int qty = si.getInitialAmount();
        if (event.isShiftClick() && category != ShopCategory.ENCHANTS && category != ShopCategory.INVESTMENTS) {
            qty = Math.min(10, si.getInitialAmount());
        }

        if (category == ShopCategory.ENCHANTS) {
            handleEnchantPurchase(player, pdcValue, category);
        } else if (category == ShopCategory.INVESTMENTS) {
            handleInvestmentPurchase(player, pdcValue);
        } else {
            handleShopItemClick(player, pdcValue, category, qty);
        }
    }

    private void handleUndoPurchase(Player player, ShopCategory category) {
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

        // Refund
        ccp.addCoins(rec.price());

        // Remove the purchased item(s) according to the recorded quantity
        int qty = rec.quantity();
        boolean removed = removeItemFromPlayer(player, rec.item().name(), qty);

        // Restore the replaced item if there was one
        if (rec.replacedItem() != null) {
            restoreReplacedItem(player, rec.replacedItem());
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) + " and restored your previous item.</green>");
        } else {
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) +
                    (removed ? "" : " (could not find item(s) to remove)") + "</green>");
        }

        SoundUtils.play(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);

         // Refresh the GUI to show updated state
         ShopGUI.openCategoryItems(player, category);
    }

    private void restoreReplacedItem(Player player, ItemStack replacedItem) {
        if (replacedItem == null) return;

        Material m = replacedItem.getType();
        PlayerInventory inv = player.getInventory();

        // Restore to appropriate slot based on item type
        if (m.name().endsWith("HELMET")) {
            inv.setHelmet(replacedItem);
        } else if (m.name().endsWith("CHESTPLATE")) {
            inv.setChestplate(replacedItem);
        } else if (m.name().endsWith("LEGGINGS")) {
            inv.setLeggings(replacedItem);
        } else if (m.name().endsWith("BOOTS")) {
            inv.setBoots(replacedItem);
        } else if (m.name().contains("SWORD") || m.name().contains("AXE") ||
                   m.name().contains("PICKAXE") || m.name().contains("SHOVEL")) {
            // Find an empty slot or add to inventory
            int emptySlot = inv.firstEmpty();
            if (emptySlot != -1) {
                inv.setItem(emptySlot, replacedItem);
            } else {
                inv.addItem(replacedItem);
            }
        } else {
            inv.addItem(replacedItem);
        }
    }

    private boolean removeItemFromPlayer(Player player, String itemTag, int quantity) {
        int remaining = Math.max(1, quantity);
        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is == null || !is.hasItemMeta()) continue;

            ItemMeta meta = is.getItemMeta();
            String val = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
            if (val != null && val.equals(itemTag)) {
                int amt = is.getAmount();
                if (amt > remaining) {
                    is.setAmount(amt - remaining);
                    remaining = 0;
                } else {
                    player.getInventory().setItem(i, null);
                    remaining -= amt;
                }
            }
        }

        // Check off-hand
        if (remaining > 0) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off.getType() != Material.AIR && off.hasItemMeta()) {
                ItemMeta meta = off.getItemMeta();
                String val = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
                if (val != null && val.equals(itemTag)) {
                    int amt = off.getAmount();
                    if (amt > remaining) {
                        off.setAmount(amt - remaining);
                        remaining = 0;
                    } else {
                        player.getInventory().setItemInOffHand(null);
                        remaining -= amt;
                    }
                }
            }
        }

        // Check armor slots (each armor piece counts as 1)
        if (remaining > 0) {
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (int i = 0; i < armor.length && remaining > 0; i++) {
                ItemStack is = armor[i];
                if (is == null || !is.hasItemMeta()) continue;

                ItemMeta meta = is.getItemMeta();
                String val = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
                if (val != null && val.equals(itemTag)) {
                    armor[i] = null;
                    player.getInventory().setArmorContents(armor);
                    remaining -= 1;
                }
            }
        }

        return remaining < Math.max(1, quantity);
    }

    private void handleEnchantPurchase(Player player, String pdcValue, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        EnchantEntry ee;
        try {
            ee = EnchantEntry.valueOf(pdcValue);
        } catch (IllegalArgumentException e) {
            return;
        }

        int currentLevel = ccp.getOwnedEnchantLevel(ee);
        int nextLevel = currentLevel + 1;

        if (nextLevel > ee.getMaxLevel()) {
            Messages.send(player, "<yellow>You already have the maximum level of this enchant!</yellow>");
            return;
        }

        long price = ee.getPriceForLevel(nextLevel);
        if (!ccp.canAfford(price)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.deductCoins(price);
        ccp.addOwnedEnchant(ee, nextLevel);

        ItemUtils.applyEnchantToBestItem(player, ee, nextLevel);
        Messages.send(player, "<green>Purchased " + ee.getDisplayName() + " " + nextLevel + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        // Refresh the GUI to show updated enchant levels
        ShopGUI.openCategoryItems(player, category);
    }

    private void handleInvestmentPurchase(Player player, String pdcValue) {
        // TODO: Implement investment purchase logic
        Messages.send(player, "<yellow>Investments coming soon!</yellow>");
    }

    private void handleShopItemClick(Player player, String pdcValue, ShopCategory category, int quantity) {
         ShopItem si;
         try {
             si = ShopItem.valueOf(pdcValue);
         } catch (IllegalArgumentException e) {
             return;
         }

        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        long price = si.getPrice();
        long totalPrice = price * Math.max(1, quantity);
        if (!ccp.canAfford(totalPrice)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (si == ShopItem.DEATHMAULER_OUTFIT || si == ShopItem.DRAGON_SET) {
            // special sets are single-quantity items
            handleSpecialSet(player, si, ccp, price, category);
            return;
        }

        // Check diamond prerequisite for upgradable items
        if (isUpgradableItem(si) && si.getMaterial().name().contains("DIAMOND")) {
            if (!hasIronEquivalent(player, si)) {
                Messages.send(player, "<red>You must buy the iron version first!</red>");
                SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // Deduct coins and give item(s)
        ccp.deductCoins(totalPrice);
        giveItemToPlayer(player, ccp, si, quantity, totalPrice);

        ShopGUI.openCategoryItems(player, category);
    }

    private void handleSpecialSet(Player player, ShopItem si, CashClashPlayer ccp, long price, ShopCategory category) {
        // special sets are always single-quantity purchases
        if (si == ShopItem.DEATHMAULER_OUTFIT) {
            ItemUtils.giveCustomArmorSet(player, CustomArmor.DEATHMAULER_CHESTPLATE, CustomArmor.DEATHMAULER_LEGGINGS);
        } else if (si == ShopItem.DRAGON_SET) {
            ItemUtils.giveCustomArmorSet(player, CustomArmor.DRAGON_CHESTPLATE, CustomArmor.DRAGON_BOOTS, CustomArmor.DRAGON_HELMET);
        }

        ccp.addPurchase(new PurchaseRecord(si, 1, price));
        Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(player, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
    }

    private void giveItemToPlayer(Player player, CashClashPlayer ccp, ShopItem si, int quantity, long totalPrice) {
        // For stackable items, give an ItemStack with quantity (capped by si.getInitialAmount())
        int giveQty = Math.max(1, Math.min(quantity, si.getInitialAmount()));

        ItemStack item = ItemUtils.createTaggedItem(si);
        ItemStack replacedItem;

        if (si.getCategory() == ShopCategory.ARMOR) {
            // armor cannot be bulk-bought; equip single and record replaced item
            replacedItem = ItemUtils.equipArmorOrReplace(player, item);
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem));
            ItemUtils.applyOwnedEnchantsAfterPurchase(player, si);
            Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            return;
        }

        if (si.getCategory() == ShopCategory.WEAPONS) {
            // weapons treated single-quantity (can't bulk replace tools reasonably)
            replacedItem = ItemUtils.replaceBestMatchingTool(player, item);
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem));
            ItemUtils.applyOwnedEnchantsAfterPurchase(player, si);
            Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            return;
        }

        // Utility/food/custom stackable items
        ItemStack stack = item.clone();
        stack.setAmount(giveQty);
        player.getInventory().addItem(stack);
        ccp.addPurchase(new PurchaseRecord(si, giveQty, totalPrice));
        Messages.send(player, "<green>Purchased " + si.getDisplayName() + " x" + giveQty + " for $" + String.format("%,d", totalPrice) + "</green>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    private boolean isUpgradableItem(ShopItem si) {
        String name = si.name();
        return name.contains("IRON_SWORD") || name.contains("DIAMOND_SWORD") ||
               name.contains("IRON_AXE") || name.contains("DIAMOND_AXE") ||
               name.contains("IRON_HELMET") || name.contains("DIAMOND_HELMET") ||
               name.contains("IRON_CHESTPLATE") || name.contains("DIAMOND_CHESTPLATE") ||
               name.contains("IRON_LEGGINGS") || name.contains("DIAMOND_LEGGINGS") ||
               name.contains("IRON_BOOTS") || name.contains("DIAMOND_BOOTS");
    }

    private boolean hasIronEquivalent(Player player, ShopItem diamondItem) {
        String ironName = diamondItem.getMaterial().name().replace("DIAMOND", "IRON");
        Material ironMaterial;
        try {
            ironMaterial = Material.valueOf(ironName);
        } catch (IllegalArgumentException e) {
            return true; // Not an upgradable item
        }

        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == ironMaterial) {
                return true;
            }
        }

        for (ItemStack is : player.getInventory().getArmorContents()) {
            if (is != null && is.getType() == ironMaterial) {
                return true;
            }
        }

        return false;
    }
}
