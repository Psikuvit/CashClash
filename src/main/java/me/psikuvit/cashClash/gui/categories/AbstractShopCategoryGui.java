package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Abstract base class for shop category GUIs.
 * Provides common functionality for all shop categories.
 */
public abstract class AbstractShopCategoryGui extends AbstractGui {

    protected final ShopCategory category;

    protected AbstractShopCategoryGui(String guiId, Player viewer, ShopCategory category) {
        super(guiId, viewer);
        this.category = category;
        setTitle("<gold><bold>" + category.getDisplayName() + "</bold></gold>");
        setRows(6);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void build() {
        populateItems();
        addBottomRow();
    }

    /**
     * Populate the category-specific items.
     * Subclasses must implement this to add their items.
     */
    protected abstract void populateItems();

    /**
     * Add the bottom row with back, undo, and coins display.
     */
    protected void addBottomRow() {
        // Back button
        setBackButton(45, p -> new ShopGUI(p).open());

        // Undo button
        setButton(49, GuiButton.of(GuiItemUtils.createUndoButton())
                .onClick(p -> handleUndoPurchase()));

        // Coins display
        long coins = getPlayerCoins();
        setButton(53, GuiButton.of(GuiItemUtils.createCoinDisplay(coins)));
    }

    /**
     * Get the player's current coin balance.
     */
    protected long getPlayerCoins() {
        GameSession session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) return 0;
        CashClashPlayer ccp = session.getCashClashPlayer(viewer.getUniqueId());
        return ccp != null ? ccp.getCoins() : 0;
    }

    /**
     * Get the CashClashPlayer for the viewer.
     */
    protected CashClashPlayer getCashClashPlayer() {
        GameSession session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) return null;
        return session.getCashClashPlayer(viewer.getUniqueId());
    }

    /**
     * Get the current game session.
     */
    protected GameSession getSession() {
        return GameManager.getInstance().getPlayerSession(viewer);
    }

    /**
     * Check if the player has a specific item in their inventory.
     */
    protected boolean hasItem(Material material) {
        for (ItemStack is : viewer.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                return true;
            }
        }
        for (ItemStack is : viewer.getInventory().getArmorContents()) {
            if (is != null && is.getType() == material) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle purchasing a standard purchasable item.
     */
    protected void handlePurchasableClick(Purchasable item, ClickType clickType) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You must be in a game to shop.</red>");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        int qty = item.getInitialAmount();
        if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
            qty = Math.min(10, item.getInitialAmount());
        }

        long price = item.getPrice();
        long totalPrice = price * Math.max(1, qty);

        if (!ShopService.getInstance().canAfford(viewer, totalPrice)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().purchase(viewer, totalPrice);
        giveItemToPlayer(ccp, item, qty, totalPrice);
        refresh();
    }

    /**
     * Give the purchased item to the player.
     */
    protected void giveItemToPlayer(CashClashPlayer ccp, Purchasable si, int quantity, long totalPrice) {
        int giveQty = Math.max(1, Math.min(quantity, si.getInitialAmount()));

        ItemStack item = ItemUtils.createTaggedItem(si);
        ItemStack replacedItem;

        GameSession sess = getSession();
        int round = sess != null ? sess.getCurrentRound() : 1;

        if (si.getCategory() == ShopCategory.ARMOR) {
            replacedItem = ItemUtils.equipArmorOrReplace(viewer, item);
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem, round));
            ItemUtils.applyOwnedEnchantsAfterPurchase(viewer, si);
            Messages.send(viewer, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else if (si.getCategory() == ShopCategory.WEAPONS) {
            replacedItem = ItemUtils.replaceBestMatchingTool(viewer, item);
            ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice(), replacedItem, round));
            ItemUtils.applyOwnedEnchantsAfterPurchase(viewer, si);
            Messages.send(viewer, "<green>Purchased " + si.getDisplayName() + " for $" + String.format("%,d", si.getPrice()) + "</green>");
            SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        } else {
            ItemStack stack = item.clone();
            stack.setAmount(giveQty);
            viewer.getInventory().addItem(stack);
            ccp.addPurchase(new PurchaseRecord(si, giveQty, totalPrice, round));
            Messages.send(viewer, "<green>Purchased " + si.getDisplayName() + " x" + giveQty + " for $" + String.format("%,d", totalPrice) + "</green>");
            SoundUtils.play(viewer, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    /**
     * Handle undo purchase for this category.
     */
    protected void handleUndoPurchase() {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You're not in a game.</red>");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        PurchaseRecord rec = ccp.peekLastPurchase();
        if (rec == null || rec.round() != sess.getCurrentRound()) {
            Messages.send(viewer, "<red>No purchase to undo.</red>");
            return;
        }

        if (rec.item().getCategory() != category) {
            Messages.send(viewer, "<red>No purchase to undo in this category.</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.popLastPurchase();
        ShopService.getInstance().refund(viewer, rec.price());

        int qty = rec.quantity();
        boolean removed = ItemUtils.removeItemFromPlayer(viewer, rec.item().name(), qty);

        if (rec.replacedItem() != null) {
            ItemUtils.restoreReplacedItem(viewer, rec.replacedItem());
            Messages.send(viewer, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) + " and restored your previous item.</green>");
        } else {
            Messages.send(viewer, "<green>Purchase undone. Refunded $" + String.format("%,d", rec.price()) +
                    (removed ? "" : " (could not find item(s) to remove)") + "</green>");
        }

        SoundUtils.play(viewer, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
        refresh();
    }

    /**
     * Refresh this GUI (rebuild and reopen).
     */
    public void refresh() {
        open();
    }

    /**
     * Get the shop category for this GUI.
     */
    public ShopCategory getCategory() {
        return category;
    }

    /**
     * Create a purchasable button with click handler.
     */
    protected GuiButton createPurchasableButton(Purchasable item, boolean maxed) {
        ItemStack itemStack = GuiItemUtils.createUpgradableItem(item, maxed);
        return GuiButton.of(itemStack).onClick((p, clickType) -> {
            if (maxed) {
                Messages.send(p, "<yellow>You already have the maximum tier of this item!</yellow>");
                SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            handlePurchasableClick(item, clickType);
        });
    }

    /**
     * Create a shop item button with click handler.
     */
    protected GuiButton createShopItemButton(Purchasable item, int quantity) {
        ItemStack itemStack = GuiItemUtils.createShopItem(viewer, item, quantity);
        return GuiButton.of(itemStack).onClick((p, clickType) -> handlePurchasableClick(item, clickType));
    }
}

