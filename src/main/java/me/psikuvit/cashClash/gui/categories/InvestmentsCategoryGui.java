package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import me.psikuvit.cashClash.util.items.ItemFactory;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shop category GUI for investments.
 */
public class InvestmentsCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_investments";

    public InvestmentsCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.INVESTMENTS);
    }

    @Override
    protected void populateItems() {
        setButton(20, createInvestmentButton(InvestmentType.WALLET));
        setButton(22, createInvestmentButton(InvestmentType.PURSE));
        setButton(24, createInvestmentButton(InvestmentType.ENDER_BAG));
    }

    private GuiButton createInvestmentButton(InvestmentType type) {
        ItemStack itemStack = ItemFactory.getInstance().getGuiFactory().createInvestmentIcon(type);
        return GuiButton.of(itemStack).onClick(p -> handleInvestmentPurchase(type));
    }

    private void handleInvestmentPurchase(InvestmentType type) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "shop.must-be-in-game");
            viewer.closeInventory();
            return;
        }

        if (sess.getCurrentRound() >= 6) {
            Messages.send(viewer, "shop.investment-round-locked");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        if (ccp.getCurrentInvestment() != null) {
            Messages.send(viewer, "shop.investment-already-active",
                    "type", ccp.getCurrentInvestment().getType().name().replace("_", " "));
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long cost = type.getCost();
        if (!ShopService.getInstance().canAfford(viewer, cost)) {
            Messages.send(viewer, "shop.not-enough-coins", "cost", String.format("%,d", cost));
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().processPurchase(viewer, type, 1, cost);

        Investment investment = new Investment(type, cost);
        ccp.setCurrentInvestment(investment);
        ccp.setInvestedCoins(cost);

        int round = sess.getCurrentRound();
        ccp.addPurchase(new PurchaseRecord(type, 1, cost, round));

        String displayName = type.name().replace("_", " ");
        Messages.send(viewer, "shop.investment-purchased",
                "cost", String.format("%,d", cost), "type", displayName);
        Messages.send(viewer, "shop.investment-details",
                "bonus", String.format("%,d", type.getBonusReturn()),
                "negative", String.format("%,d", type.getNegativeReturn()));
        Messages.send(viewer, "shop.investment-rules");

        SoundUtils.play(viewer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        refresh();
    }

    @Override
    protected void handleUndoPurchase() {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "generic.player-not-in-game");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        PurchaseRecord rec = ccp.peekLastPurchase();
        if (rec == null || rec.round() != sess.getCurrentRound()) {
            Messages.send(viewer, "shop.no-purchase-undo");
            return;
        }

        if (rec.item().getCategory() != ShopCategory.INVESTMENTS) {
            Messages.send(viewer, "shop.no-purchase-undo-category");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.popLastPurchase();
        ShopService.getInstance().refund(viewer, rec.price());

        // Clear the investment
        ccp.setCurrentInvestment(null);
        ccp.setInvestedCoins(0);
        Messages.send(viewer, "shop.investment-cancelled", "price", String.format("%,d", rec.price()));
        SoundUtils.play(viewer, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);

        refresh();
    }
}

