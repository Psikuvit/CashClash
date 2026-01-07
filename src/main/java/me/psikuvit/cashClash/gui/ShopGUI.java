package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.gui.categories.AbstractShopCategoryGui;
import me.psikuvit.cashClash.gui.categories.ArmorCategoryGui;
import me.psikuvit.cashClash.gui.categories.CustomItemsCategoryGui;
import me.psikuvit.cashClash.gui.categories.EnchantsCategoryGui;
import me.psikuvit.cashClash.gui.categories.FoodCategoryGui;
import me.psikuvit.cashClash.gui.categories.InvestmentsCategoryGui;
import me.psikuvit.cashClash.gui.categories.MythicCategoryGui;
import me.psikuvit.cashClash.gui.categories.UtilityCategoryGui;
import me.psikuvit.cashClash.gui.categories.WeaponsCategoryGui;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
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
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void build() {
        setButton(12, createCategoryButton(ShopCategory.WEAPONS, Material.IRON_AXE));
        setButton(13, createCategoryButton(ShopCategory.ARMOR, Material.DIAMOND_CHESTPLATE));
        setButton(14, createCategoryButton(ShopCategory.FOOD, Material.GOLDEN_APPLE));

        setButton(21, createCategoryButton(ShopCategory.ENCHANTS, Material.ENCHANTING_TABLE));
        setButton(22, createCategoryButton(ShopCategory.UTILITY, Material.WATER_BUCKET));
        setButton(23, createCategoryButton(ShopCategory.CUSTOM_ITEMS, Material.NAME_TAG));

        setButton(31, createInvestmentCategoryButton());

        // Mythic items section
        addMythicItems();

        // Balance display
        long coins = getPlayerCoins();
        setButton(53, GuiButton.of(GuiItemUtils.createCoinDisplay(coins)));

        // Cancel button
        setCloseButton(45);
    }

    private GuiButton createCategoryButton(ShopCategory category, Material icon) {
        ItemStack item = GuiItemUtils.createCategoryIcon(icon, category);
        return GuiButton.of(item).onClick(p -> openCategory(category));
    }

    private GuiButton createInvestmentCategoryButton() {
        ItemStack bundle = new ItemStack(Material.RED_BUNDLE);
        BundleMeta bundleMeta = (BundleMeta) bundle.getItemMeta();
        bundleMeta.displayName(Messages.parse("<yellow>" + ShopCategory.INVESTMENTS.getDisplayName() + "</yellow>"));
        bundleMeta.lore(Messages.wrapLines("<gray>Click to browse investments</gray>"));
        bundleMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bundle.setItemMeta(bundleMeta);

        return GuiButton.of(bundle).onClick(p -> openCategory(ShopCategory.INVESTMENTS));
    }

    private void addMythicItems() {
        int[] legendSlots = {38, 39, 40, 41, 42};
        String[] legendColors = {"RED", "ORANGE", "YELLOW", "GREEN", "BLUE"};

        GameSession session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) {
            return;
        }

        if (session.getCurrentRound() == 1) {
            // Round 1 - show locked mythics
            for (int slot : legendSlots) {
                setItem(slot, ItemStack.empty());
                setItem(slot + 9, createPane(Material.BLACK_STAINED_GLASS_PANE));
            }
            return;
        }

        // Round 2+ - show available mythics
        List<MythicItem> availableMythics = MythicItemManager.getInstance().getAvailableMythics(session);
        UUID playerUuid = viewer.getUniqueId();
        boolean playerHasMythic = MythicItemManager.getInstance().hasPlayerPurchasedMythic(session, playerUuid);
        MythicItem ownedMythic = MythicItemManager.getInstance().getPlayerMythic(session, playerUuid);

        for (int i = 0; i < availableMythics.size() && i < legendSlots.length; i++) {
            MythicItem mythic = availableMythics.get(i);
            boolean mythicTaken = !MythicItemManager.getInstance().isMythicAvailableInSession(session, mythic);
            UUID ownerUuid = MythicItemManager.getInstance().getMythicOwner(session, mythic);

            ItemStack mythicItem = GuiItemUtils.createMythicShopItem(mythic, playerHasMythic, ownedMythic, mythicTaken, ownerUuid);
            setButton(legendSlots[i], GuiButton.of(mythicItem)
                    .onClick(p -> MythicCategoryGui.handleMythicPurchase(p, mythic, this)));

            String material = legendColors[i] + "_STAINED_GLASS_PANE";
            ItemStack itemStack = new ItemStack(Material.valueOf(material));
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(Component.empty());
            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(itemMeta);
            setItem(legendSlots[i] + 9, itemStack);
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
            case ENCHANTS -> new EnchantsCategoryGui(viewer);
            case INVESTMENTS -> new InvestmentsCategoryGui(viewer);
            default -> throw new IllegalArgumentException("Unknown shop category: " + category);
        };
    }

    private long getPlayerCoins() {
        var session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) return 0;
        CashClashPlayer ccp = session.getCashClashPlayer(viewer.getUniqueId());
        return ccp != null ? ccp.getCoins() : 0;
    }

    // ==================== STATIC CONVENIENCE METHODS ====================

    /**
     * Static convenience method to open the main shop GUI.
     */
    public static void openMain(Player player) {
        new ShopGUI(player).open();
    }

}
