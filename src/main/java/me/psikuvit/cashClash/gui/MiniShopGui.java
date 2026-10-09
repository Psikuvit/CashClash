package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ShopItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A team's mini shop villager's shop, open during combat. It sells a few supplies in their
 * main-shop bundles, each for the main-shop price plus the mini-shop markup
 * ({@code mini-shop.markup-percent} in shop.yml).
 */
public class MiniShopGui extends AbstractGui {

    private static final String GUI_ID = "shop_mini";

    public MiniShopGui(Player viewer) {
        super(GUI_ID, viewer);
        setTitle("<gold><bold>Mini Shop</bold></gold>");
        setRows(3);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    protected void build() {
        setButton(11, supplyButton(UtilityItem.LEAVES));
        setButton(12, supplyButton(UtilityItem.ARROWS));
        setButton(14, supplyButton(FoodItem.BREAD));
        setButton(15, supplyButton(FoodItem.STEAK));
    }

    /**
     * What one bundle (the item's main-shop amount) costs here.
     */
    public static long price(Purchasable item) {
        long mainShopPrice = item.getPrice() * item.getInitialAmount();
        int markupPercent = CashClashPlugin.getInstance().getShopConfig().getMiniShopMarkupPercent();
        return Math.round(mainShopPrice * (100 + markupPercent) / 100.0);
    }

    private GuiButton supplyButton(Purchasable item) {
        long price = price(item);
        ItemStack icon = ShopItemBuilder.of(item.getMaterial(), item.getInitialAmount())
                .name("<yellow>" + item.getDisplayName() + "</yellow>")
                .price(price)
                .purchasePrompt()
                .itemId(item.name())
                .build();
        return GuiButton.of(icon).onClick(p -> buy(item, price));
    }

    private void buy(Purchasable item, long price) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(viewer);
        if (session == null || session.getState() != GameState.COMBAT) {
            viewer.closeInventory();
            return;
        }

        ShopService shop = CashClashPlugin.getInstance().getShopService();
        if (!shop.canAfford(viewer, price)) {
            Messages.send(viewer, "shop.not-enough-coins", "cost", String.format("%,d", price));
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        shop.processPurchase(viewer, item, item.getInitialAmount(), price);
        open();
    }
}
