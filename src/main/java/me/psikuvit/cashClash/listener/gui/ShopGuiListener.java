package me.psikuvit.cashClash.listener.gui;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.GuiType;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import me.psikuvit.cashClash.util.items.ItemUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
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

        // Check for mythic purchase (slots 38-42)
        int slot = event.getSlot();
        if (slot >= 38 && slot <= 42) {
            handleMythicPurchase(player, clicked);
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

        clicked.getItemMeta();

        if (event.getSlot() == 45) {
            ShopGUI.openMain(player);
            return;
        }

        if (event.getSlot() == 49) {
            handleUndoPurchase(player, sh.getCategory());
            return;
        }

        // Check if item is maxed/owned using PDC flag
        Byte maxedFlag = PDCDetection.getMaxedFlag(clicked);
        if (maxedFlag != null && maxedFlag == (byte) 1) {
            Messages.send(player, "<yellow>You already have the maximum tier of this item!</yellow>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopCategory category = sh.getCategory();
        if (category == ShopCategory.ENCHANTS) {
            handleEnchantPurchase(player, clicked, category);
            return;
        } else if (category == ShopCategory.INVESTMENTS) {
            handleInvestmentPurchase(player, clicked);
            return;
        } else if (sh.getCategory() == ShopCategory.CUSTOM_ITEMS) {
            handleCustomItemPurchase(player, clicked, sh.getCategory());
            return;
        }

        // Get item tag from PDC
        String itemTag = PDCDetection.getAnyShopTag(clicked);
        if (itemTag != null && itemTag.startsWith("SET_")) {
            handleArmorSetPurchase(player, itemTag);
            return;
        }

        Purchasable si = PDCDetection.getPurchasable(clicked);
        if (si == null) {
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Unknown shop item: " + clicked);
            return;
        }

        int qty = si.getInitialAmount();
        if (event.isShiftClick()) {
            qty = Math.min(10, si.getInitialAmount());
        }

        handleShopItemClick(player, si, category, qty);
    }

    private void handleUndoPurchase(Player player, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        PurchaseRecord rec = ccp.peekLastPurchase();
        if (rec == null || rec.round() != sess.getCurrentRound()) {
            Messages.send(player, "<red>No purchase to undo.</red>");
            return;
        }

        // Check if the last purchase is from the same category
        if (rec.item().getCategory() != category) {
            Messages.send(player, "<red>No purchase to undo in this category.</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.popLastPurchase();
        ShopService.getInstance().refund(player, rec.price());

        // Handle investment-specific undo (clear the investment)
        if (category == ShopCategory.INVESTMENTS) {
            ccp.setCurrentInvestment(null);
            ccp.setInvestedCoins(0);
            Messages.send(player, "<green>Investment cancelled. Refunded $" + String.format("%,d", rec.price()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
            ShopGUI.openCategoryItems(player, category);
            return;
        }

        // Remove the purchased item(s) according to the recorded quantity
        int qty = rec.quantity();
        boolean removed = ItemUtils.removeItemFromPlayer(player, rec.item().name(), qty);

        // Restore the replaced item if there was one
        if (rec.replacedItem() != null) {
            ItemUtils.restoreReplacedItem(player, rec.replacedItem());
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) + " and restored your previous item.</green>");
        } else {
            Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) +
                    (removed ? "" : " (could not find item(s) to remove)") + "</green>");
        }

        SoundUtils.play(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);

        if (category != null) {
            ShopGUI.openCategoryItems(player, category);
        } else {
            ShopGUI.openMain(player);
        }
    }

    private void handleEnchantPurchase(Player player, ItemStack stack, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        try {
            EnchantEntry ee = PDCDetection.getEnchantEntry(stack);
            if (ee == null) return;
            int currentLevel = ccp.getOwnedEnchantLevel(ee);
            int nextLevel = currentLevel + 1;

            if (nextLevel > ee.getMaxLevel()) {
                Messages.send(player, "<yellow>You already have the maximum level of this enchant!</yellow>");
                return;
            }

            long price = ee.getPriceForLevel(nextLevel);
            if (!ShopService.getInstance().canAfford(player, price)) {
                Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
                SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            ShopService.getInstance().purchase(player, price);
            ccp.addOwnedEnchant(ee, nextLevel);

            ItemUtils.applyEnchantToBestItem(player, ee, nextLevel);
            Messages.send(player, "<green>Purchased " + ee.getDisplayName() + " " + nextLevel + " for $" + String.format("%,d", price) + "</green>");

        } catch (IllegalArgumentException e) {
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Unknown enchant entry", e);
        }

        SoundUtils.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        ShopGUI.openCategoryItems(player, category);
    }

    private void handleInvestmentPurchase(Player player, ItemStack stack) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        // Cannot buy investments in Round 5
        if (sess.getCurrentRound() >= 5) {
            Messages.send(player, "<red>Investments cannot be purchased in Round 5!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        // Check if player already has an investment
        if (ccp.getCurrentInvestment() != null) {
            Messages.send(player, "<red>You already have an active investment! (" +
                    ccp.getCurrentInvestment().getType().name().replace("_", " ") + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        InvestmentType type = PDCDetection.getInvestment(stack);
        if (type == null) {
            Messages.send(player, "<red>Invalid investment type!</red>");
            return;
        }

        long cost = type.getCost();
        if (!ShopService.getInstance().canAfford(player, cost)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", cost) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct coins and create investment
        ShopService.getInstance().purchase(player, cost);

        Investment investment = new Investment(type, cost);
        ccp.setCurrentInvestment(investment);
        ccp.setInvestedCoins(cost);

        // Record purchase for undo functionality
        int round = sess.getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(type, 1, cost, round));

        String displayName = type.name().replace("_", " ");
        Messages.send(player, "<green>You invested <gold>$" + String.format("%,d", cost) +
                "</gold> in a <yellow>" + displayName + "</yellow>!</green>");
        Messages.send(player, "<gray>Bonus: <green>$" + String.format("%,d", type.getBonusReturn()) +
                "</green> | Negative: <red>$" + String.format("%,d", type.getNegativeReturn()) + "</red></gray>");
        Messages.send(player, "<gray>1 death = Bonus | 2 deaths = Break even | 3+ deaths = Loss</gray>");

        SoundUtils.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Refresh GUI
        ShopGUI.openCategoryItems(player, ShopCategory.INVESTMENTS);
    }

    private void handleShopItemClick(Player player, Purchasable si, ShopCategory category, int quantity) {
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
        if (!ShopService.getInstance().canAfford(player, totalPrice)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().purchase(player, totalPrice);
        giveItemToPlayer(player, ccp, si, quantity, totalPrice);

        ShopGUI.openCategoryItems(player, category);
    }

    /**
     * Handles purchasing a complete armor set (must buy all pieces together).
     */
    private void handleArmorSetPurchase(Player player, String pdcValue) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        String setName = pdcValue.substring(4); // Remove "SET_" prefix

        CustomArmorItem.ArmorSet armorSet;
        try {
            armorSet = CustomArmorItem.ArmorSet.valueOf(setName);
        } catch (IllegalArgumentException e) {
            CashClashPlugin.getInstance().getLogger().warning("Unknown armor set: " + setName);
            return;
        }

        long totalPrice = armorSet.getTotalPrice();

        // Check if player can afford the entire set
        if (!ShopService.getInstance().canAfford(player, totalPrice)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct coins for the entire set
        ShopService.getInstance().purchase(player, totalPrice);

        int round = sess.getCurrentRound();

        // Give each piece of the set to the player
        for (CustomArmorItem piece : armorSet.getPieces()) {
            ItemStack replacedItem = ItemUtils.giveCustomArmorSet(player, piece);
            ccp.addPurchase(new PurchaseRecord(piece, 1, piece.getPrice(), replacedItem, round));
        }

        Messages.send(player, "");
        Messages.send(player, "<green><bold>✓ SET PURCHASED</bold></green>");
        Messages.send(player, "<yellow>" + armorSet.getDisplayName() + "</yellow>");
        Messages.send(player, "<dark_gray>-$" + String.format("%,d", totalPrice) + "</dark_gray>");
        Messages.send(player, "");
        SoundUtils.play(player, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);

        // Refresh the armor category
        ShopGUI.openCategoryItems(player, ShopCategory.ARMOR);
    }

    private void handleCustomItemPurchase(Player player, ItemStack stack, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        CustomItem type = PDCDetection.getCustomItem(stack);
        if (type == null) {
            CashClashPlugin.getInstance().getLogger().warning("Unknown custom item type");
            return;
        }

        long price = type.getPrice();
        if (!ShopService.getInstance().canAfford(player, price)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack customItem = ItemUtils.createCustomItem(type, player);

        ShopService.getInstance().purchase(player, price);
        player.getInventory().addItem(customItem);

        // Record the purchase for undo (custom items implement Purchasable)
        int round = sess.getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(type, 1, price, round));

        Messages.send(player, "<green>Purchased " + type.getDisplayName() + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Refresh GUI
        ShopGUI.openCategoryItems(player, category);
    }

    private void handleMythicPurchase(Player player, ItemStack stack) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        MythicItem mythic = PDCDetection.getMythic(stack);
        if (mythic == null) {
            CashClashPlugin.getInstance().getLogger().warning("Unknown mythic item");
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if mythic is available in this session's random selection
        if (!MythicItemManager.getInstance().isMythicAvailableInSession(sess, mythic)) {
            Messages.send(player, "<red>This mythic is not available in this game!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if this mythic has already been purchased by anyone
        if (MythicItemManager.getInstance().isMythicPurchased(sess, mythic)) {
            UUID ownerUuid = MythicItemManager.getInstance().getMythicOwner(sess, mythic);
            String ownerName = ownerUuid != null ? Bukkit.getOfflinePlayer(ownerUuid).getName() : "Someone";
            Messages.send(player, "<red>This mythic has already been purchased by " + ownerName + "!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if player already has a mythic
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

        // Deduct coins and register purchase
        ShopService.getInstance().purchase(player, price);
        MythicItemManager.getInstance().registerMythicPurchase(sess, playerUuid, mythic);

        ItemStack mythicItem = MythicItemManager.getInstance().createMythicItem(mythic, player);
        player.getInventory().addItem(mythicItem);

        // Give 20 free arrows if purchasing Wind Bow
        if (mythic == MythicItem.WIND_BOW) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, 20));
        }

        Messages.send(player, "");
        Messages.send(player, "<dark_purple><bold>✦ MYTHIC ACQUIRED ✦</bold></dark_purple>");
        Messages.send(player, "<light_purple>" + mythic.getDisplayName() + "</light_purple>");
        Messages.send(player, "<dark_gray>-$" + String.format("%,d", price) + "</dark_gray>");
        Messages.send(player, "");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // Refresh the main menu to show updated state
        ShopGUI.openMain(player);
    }

    private void giveItemToPlayer(Player player, CashClashPlayer ccp, Purchasable si, int quantity, long totalPrice) {
        int giveQty = Math.max(1, Math.min(quantity, si.getInitialAmount()));

        ItemStack item = ItemUtils.createTaggedItem(si);
        ItemStack replacedItem;

        if (si.getCategory() == ShopCategory.ARMOR) {
            replacedItem = ItemUtils.equipArmorOrReplace(player, item);

            int round = GameManager.getInstance().getPlayerSession(player).getCurrentRound();
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem, round));

            ItemUtils.applyOwnedEnchantsAfterPurchase(player, si);
            Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else if (si.getCategory() == ShopCategory.WEAPONS) {
            replacedItem = ItemUtils.replaceBestMatchingTool(player, item);

            int round = GameManager.getInstance().getPlayerSession(player).getCurrentRound();
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem, round));

            ItemUtils.applyOwnedEnchantsAfterPurchase(player, si);
            Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else {
            ItemStack stack = item.clone();
            stack.setAmount(giveQty);

            player.getInventory().addItem(stack);

            int round = GameManager.getInstance().getPlayerSession(player).getCurrentRound();
            ccp.addPurchase(new PurchaseRecord(si, giveQty, totalPrice, round));
            Messages.send(player, "<green>Purchased " + si.getDisplayName() + " x" + giveQty + " for $" + String.format("%,d", totalPrice) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }
}
