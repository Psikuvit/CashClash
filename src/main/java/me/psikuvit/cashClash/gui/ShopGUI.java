package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.gui.categories.AbstractShopCategoryGui;
import me.psikuvit.cashClash.gui.categories.ArmorCategoryGui;
import me.psikuvit.cashClash.gui.categories.CustomItemsCategoryGui;
import me.psikuvit.cashClash.gui.categories.FoodCategoryGui;
import me.psikuvit.cashClash.gui.categories.MythicCategoryGui;
import me.psikuvit.cashClash.gui.categories.UtilityCategoryGui;
import me.psikuvit.cashClash.gui.categories.WeaponsCategoryGui;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Main shop GUI that provides access to all shop categories.
 * Uses the AbstractGui system with sub-GUIs for each category.
 */
public class ShopGUI extends AbstractGui {

    private static final String GUI_ID = "shop_main";

    public ShopGUI(Player viewer) {
        super(GUI_ID, viewer);
        setTitle("<gold><bold>Shop</bold></gold>");
        setRows(6);
        setFillMaterial(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void build() {
        setButton(11, createCategoryButton(ShopCategory.WEAPONS, Material.IRON_AXE));
        setButton(12, createCategoryButton(ShopCategory.UTILITY, Material.WATER_BUCKET));
        setButton(13, createCategoryButton(ShopCategory.ARMOR, Material.DIAMOND_CHESTPLATE));
        setButton(14, createCategoryButton(ShopCategory.FOOD, Material.GOLDEN_APPLE));
        setButton(15, createCategoryButton(ShopCategory.CUSTOM_ITEMS, Material.NAME_TAG));

        // Investments system removed
        // setButton(31, createInvestmentCategoryButton());

        // Mythic items section
        addMythicItems();

        // Balance display
        long coins = getPlayerCoins();
        setButton(53, GuiButton.of(CashClashPlugin.getInstance().getItemFactory().getGuiFactory().createCoinDisplay(coins)));

        // Transfer money button (next to balance)
        setButton(52, createTransferButton());

        // Cancel button
        setCloseButton(45);
    }

    private GuiButton createCategoryButton(ShopCategory category, Material icon) {
        ItemStack item = CashClashPlugin.getInstance().getItemFactory().getGuiFactory().createCategoryIcon(icon, category);
        return GuiButton.of(item).onClick(p -> openCategory(category));
    }

    private GuiButton createInvestmentCategoryButton() {
        ItemStack icon = CashClashPlugin.getInstance().getItemFactory().getGuiFactory().createCategoryIcon(Material.RED_BUNDLE, ShopCategory.INVESTMENTS);
        return GuiButton.of(icon).onClick(p -> openCategory(ShopCategory.INVESTMENTS));
    }

    private static final int[] MYTHIC_SLOTS = {30, 32, 39, 41};
    private static final int[] MYTHIC_SEPARATOR_SLOTS = {31, 40};

    /**
     * The session's random mythics in a 2x2 grid, split down the middle by two black panes.
     * Round 1 has no mythics yet: the four mythic slots stay blank, clear of the background glass.
     */
    private void addMythicItems() {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(viewer);
        if (session == null) {
            return;
        }

        for (int slot : MYTHIC_SEPARATOR_SLOTS) {
            setItem(slot, createPane(Material.BLACK_STAINED_GLASS_PANE));
        }
        if (session.getCurrentRound() == 1) {
            for (int slot : MYTHIC_SLOTS) {
                setItem(slot, ItemStack.empty());
            }
            return;
        }

        List<MythicItem> availableMythics = CashClashPlugin.getInstance().getMythicItemManager().getAvailableMythics(session);
        UUID playerUuid = viewer.getUniqueId();
        boolean playerHasMythic = CashClashPlugin.getInstance().getMythicItemManager().hasPlayerPurchasedMythic(session, playerUuid);
        MythicItem ownedMythic = CashClashPlugin.getInstance().getMythicItemManager().getPlayerMythic(session, playerUuid);

        for (int i = 0; i < availableMythics.size() && i < MYTHIC_SLOTS.length; i++) {
            MythicItem mythic = availableMythics.get(i);
            boolean mythicTaken = CashClashPlugin.getInstance().getMythicItemManager().isMythicPurchased(session, mythic);
            UUID ownerUuid = CashClashPlugin.getInstance().getMythicItemManager().getMythicOwner(session, mythic);

            ItemStack mythicItem = CashClashPlugin.getInstance().getItemFactory().getGuiFactory().createMythicShopItem(viewer, mythic, playerHasMythic, ownedMythic, mythicTaken, ownerUuid);
            setButton(MYTHIC_SLOTS[i], GuiButton.of(mythicItem)
                    .onClick(p -> MythicCategoryGui.handleMythicPurchase(p, mythic, this)));
        }
    }

    /**
     * Open a category sub-GUI.
     */
    public void openCategory(ShopCategory category) {
        AbstractShopCategoryGui categoryGui = createCategoryGui(category);
        openSubGui(categoryGui);
    }

    /**
     * Factory method to create the appropriate category GUI.
     */
    private AbstractShopCategoryGui createCategoryGui(ShopCategory category) {
        return switch (category) {
            case WEAPONS -> new WeaponsCategoryGui(viewer);
            case ARMOR -> new ArmorCategoryGui(viewer);
            case FOOD -> new FoodCategoryGui(viewer);
            case UTILITY -> new UtilityCategoryGui(viewer);
            case CUSTOM_ITEMS -> new CustomItemsCategoryGui(viewer);
            case INVESTMENTS -> throw new UnsupportedOperationException("Investments system has been removed");
            default -> throw new IllegalArgumentException("Unknown shop category: " + category);
        };
    }

    private long getPlayerCoins() {
        CashClashPlayer ccp = CashClashPlayer.from(viewer);
        return ccp != null ? ccp.getCoins() : 0;
    }

    private GuiButton createTransferButton() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<green>Transfer Money</green>"));
        meta.lore(List.of(
                Messages.parse("<gray>Send coins to a teammate</gray>"),
                Messages.parse("<gray>Fee: <red>10%</red></gray>"),
                Messages.parse(""),
                Messages.parse("<yellow>Click to transfer</yellow>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        return GuiButton.of(item).onClick(TransferGUI::open);
    }

    // ==================== STATIC CONVENIENCE METHODS ====================

    /**
     * Static convenience method to open the main shop GUI.
     */
    public static void openMain(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);

        // Check if player is in buff selection phase (PTP gamemode)
        if (session != null && session.getGamemode() instanceof me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode ptp) {
            if (ptp.isBuffSelectionActive()) {
                Messages.send(player, "gamestate.shop-locked");
                return;
            }
        }

        new ShopGUI(player).open();
    }

}
