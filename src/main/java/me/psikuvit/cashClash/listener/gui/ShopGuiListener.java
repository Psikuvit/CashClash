package me.psikuvit.cashClash.listener.gui;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.GuiType;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.shop.items.CustomItemType;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.player.InvestmentType;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.ItemSelectionUtils;
import me.psikuvit.cashClash.util.ItemUtils;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SoundUtils;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
            String pdcValue = meta.getPersistentDataContainer().get(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING);
            if (pdcValue != null) {
                handleMythicPurchase(player, pdcValue);
                return;
            }
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

        if (event.getSlot() == 45) {
            ShopGUI.openMain(player);
            return;
        }

        if (event.getSlot() == 49) {
            handleUndoPurchase(player, sh.getCategory());
            return;
        }

        // Check if item is maxed/owned using PDC flag
        Byte maxedFlag = itemMeta.getPersistentDataContainer().get(Keys.SHOP_ITEM_MAXED, PersistentDataType.BYTE);
        if (maxedFlag != null && maxedFlag == (byte) 1) {
            Messages.send(player, "<yellow>You already have the maximum tier of this item!</yellow>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String pdcValue = itemMeta.getPersistentDataContainer().get(Keys.SHOP_ITEM_KEY, PersistentDataType.STRING);

        ShopCategory category = sh.getCategory();
        if (category == ShopCategory.ENCHANTS) {
            handleEnchantPurchase(player, pdcValue, category);
            return;
        } else if (category == ShopCategory.INVESTMENTS) {
            handleInvestmentPurchase(player, pdcValue);
            return;
        } else if (sh.getCategory() == ShopCategory.CUSTOM_ITEMS) {
            pdcValue = itemMeta.getPersistentDataContainer().get(Keys.CUSTOM_ITEM_KEY, PersistentDataType.STRING);
            handleCustomItemPurchase(player, pdcValue, sh.getCategory());
            return;
        }

        Purchasable si = ShopItems.valueOf(pdcValue);
        if (si == null) {
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Unknown shop item: " + pdcValue);
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
        PurchaseRecord rec = ccp.popLastPurchase();
        if (rec == null || rec.round() != sess.getCurrentRound()) {
            Messages.send(player, "<red>No purchase to undo.</red>");
            return;
        }

        ccp.addCoins(rec.price());

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

    private void handleEnchantPurchase(Player player, String pdcValue, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        try {
            EnchantEntry ee = EnchantEntry.valueOf(pdcValue);
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

        } catch (IllegalArgumentException e) {
            CashClashPlugin.getInstance().getLogger().log(Level.WARNING, "Unknown enchant entry: " + pdcValue, e);
        }

        SoundUtils.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        ShopGUI.openCategoryItems(player, category);
    }

    private void handleInvestmentPurchase(Player player, String pdcValue) {
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

        InvestmentType type;
        try {
            type = InvestmentType.valueOf(pdcValue);
        } catch (IllegalArgumentException e) {
            Messages.send(player, "<red>Invalid investment type!</red>");
            return;
        }

        long cost = type.getCost();
        if (!ccp.canAfford(cost)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", cost) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct coins and create investment
        ccp.deductCoins(cost);
        
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
        if (!ccp.canAfford(totalPrice)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (si.getCategory() == ShopCategory.CUSTOM_ARMOR) {
            handleSpecialSet(player, si, ccp, price);
            return;
        }

        ccp.deductCoins(totalPrice);
        giveItemToPlayer(player, ccp, si, quantity, totalPrice);

        ShopGUI.openCategoryItems(player, category);
    }

    private void handleSpecialSet(Player player, Purchasable si, CashClashPlayer ccp, long price) {
        ccp.deductCoins(price);

        CustomArmorItem customArmor = ItemSelectionUtils.getCustomArmorItem(si);
        ItemStack replacedItem = ItemUtils.giveCustomArmorSet(player, customArmor);

        int round = GameManager.getInstance().getPlayerSession(player).getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(si, 1, price, replacedItem, round));
        Messages.send(player, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(player, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
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

    private void handleCustomItemPurchase(Player player, String pdcValue, ShopCategory category) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        CustomItemType type;
        try {
            type = CustomItemType.valueOf(pdcValue);
        } catch (IllegalArgumentException e) {
            CashClashPlugin.getInstance().getLogger().warning("Unknown custom item type: " + pdcValue);
            return;
        }

        long price = type.getPrice();
        if (!ccp.canAfford(price)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Create the custom item
        ItemStack customItem = createCustomItem(type, player);

        // Give item to player
        ccp.deductCoins(price);
        player.getInventory().addItem(customItem);

        // Record the purchase for undo (custom items implement Purchasable)
        int round = sess.getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(type, 1, price, round));

        Messages.send(player, "<green>Purchased " + type.getDisplayName() + " for $" + String.format("%,d", price) + "</green>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Refresh GUI
        ShopGUI.openCategoryItems(player, category);
    }

    private ItemStack createCustomItem(CustomItemType type, Player owner) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Messages.parse("<yellow>" + type.getDisplayName() + "</yellow>"));

        List<Component> lore = new ArrayList<>(Messages.wrapLines(type.getDescription()));
        meta.lore(lore);

        // Add PDC tags
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.CUSTOM_ITEM_KEY, PersistentDataType.STRING, type.name());
        pdc.set(Keys.CUSTOM_ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Special handling for specific items
        switch (type) {
            case BAG_OF_POTATOES -> {
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(item.getType().getMaxDurability() - 3);
                }
                meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
            }
            case CASH_BLASTER -> meta.addEnchant(Enchantment.MULTISHOT, 1, true);
            case INVIS_CLOAK -> pdc.set(Keys.CUSTOM_ITEM_USES, PersistentDataType.INTEGER, 5);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        return item;
    }

    private void handleMythicPurchase(Player player, String pdcValue) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        MythicItem mythic;
        try {
            mythic = MythicItem.valueOf(pdcValue);
        } catch (IllegalArgumentException e) {
            CashClashPlugin.getInstance().getLogger().warning("Unknown mythic item: " + pdcValue);
            return;
        }

        // Check if mythic is available in this session
        if (!MythicItemManager.getInstance().isMythicAvailableInSession(sess, mythic)) {
            Messages.send(player, "<red>This mythic is not available in this game!</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if team already has a mythic
        Team playerTeam = sess.getTeam1().hasPlayer(player.getUniqueId()) ? sess.getTeam1() : sess.getTeam2();
        if (!MythicItemManager.getInstance().canTeamPurchaseMythic(sess, playerTeam)) {
            MythicItem owned = MythicItemManager.getInstance().getTeamMythic(sess, playerTeam);
            if (owned == mythic) {
                Messages.send(player, "<yellow>Your team already owns this mythic!</yellow>");
            } else {
                Messages.send(player, "<red>Your team already owns a mythic (" + owned.getDisplayName() + ")!</red>");
            }
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long price = mythic.getPrice();
        if (!ccp.canAfford(price)) {
            Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", price) + ")</red>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct coins and register purchase
        ccp.deductCoins(price);
        MythicItemManager.getInstance().registerMythicPurchase(sess, playerTeam, mythic);

        ItemStack mythicItem = MythicItemManager.getInstance().createMythicItem(mythic, player);
        player.getInventory().addItem(mythicItem);

        Messages.send(player, "<light_purple>Purchased " + mythic.getDisplayName() + " for $" + String.format("%,d", price) + "!</light_purple>");
        Messages.send(player, "<gray>Your team now owns this mythic weapon!</gray>");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // Refresh the main menu to show updated state
        ShopGUI.openMain(player);
    }
}
