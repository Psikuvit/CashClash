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
import me.psikuvit.cashClash.util.items.ItemFactory;
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
        setButton(49, GuiButton.of(ItemFactory.getInstance().getGuiFactory().createUndoButton())
                .onClick(p -> handleUndoPurchase()));

        // Coins display
        long coins = getPlayerCoins();
        setButton(53, GuiButton.of(ItemFactory.getInstance().getGuiFactory().createCoinDisplay(coins)));
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
        if (item.getCategory() == ShopCategory.UTILITY || item.getCategory() == ShopCategory.FOOD) {
            if (clickType.isShiftClick()) {
                qty = 10;
            }
        }

        long totalPrice = ShopService.getInstance().calculateTotalPrice(item, qty);

        if (!ShopService.getInstance().canAfford(viewer, totalPrice)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().processPurchase(viewer, item, qty, totalPrice);
        refresh();
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
        Messages.debug(rec.toString());
        if (rec.item().getCategory() != category) {
            Messages.send(viewer, "<red>No purchase to undo in this category.</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.popLastPurchase();
        ShopService.getInstance().processRefund(viewer, rec);
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
    protected GuiButton createPurchasableButtonMaxed(Purchasable item, boolean maxed) {
        ItemStack itemStack = ItemFactory.getInstance().createUpgradableGuiItem(item, maxed);
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
    protected GuiButton createPurchasableButton(Purchasable item, int quantity) {
        ItemStack itemStack = ItemFactory.getInstance().createGuiItem(viewer, item, quantity);
        return GuiButton.of(itemStack).onClick((p, clickType) -> handlePurchasableClick(item, clickType));
    }
}