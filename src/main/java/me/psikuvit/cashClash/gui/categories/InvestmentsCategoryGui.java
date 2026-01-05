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
import me.psikuvit.cashClash.util.items.GuiItemUtils;
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
        ItemStack itemStack = GuiItemUtils.createInvestmentIcon(type);
        return GuiButton.of(itemStack).onClick(p -> handleInvestmentPurchase(type));
    }

    private void handleInvestmentPurchase(InvestmentType type) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You must be in a game to shop.</red>");
            viewer.closeInventory();
            return;
        }

        if (sess.getCurrentRound() >= 5) {
            Messages.send(viewer, "<red>Investments cannot be purchased in Round 5!</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        if (ccp.getCurrentInvestment() != null) {
            Messages.send(viewer, "<red>You already have an active investment! (" +
                    ccp.getCurrentInvestment().getType().name().replace("_", " ") + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long cost = type.getCost();
        if (!ShopService.getInstance().canAfford(viewer, cost)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", cost) + ")</red>");
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
        Messages.send(viewer, "<green>You invested <gold>$" + String.format("%,d", cost) +
                "</gold> in a <yellow>" + displayName + "</yellow>!</green>");
        Messages.send(viewer, "<gray>Bonus: <green>$" + String.format("%,d", type.getBonusReturn()) +
                "</green> | Negative: <red>$" + String.format("%,d", type.getNegativeReturn()) + "</red></gray>");
        Messages.send(viewer, "<gray>1 death = Bonus | 2 deaths = Break even | 3+ deaths = Loss</gray>");

        SoundUtils.play(viewer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        refresh();
    }

    @Override
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

        if (rec.item().getCategory() != ShopCategory.INVESTMENTS) {
            Messages.send(viewer, "<red>No purchase to undo in this category.</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ccp.popLastPurchase();
        ShopService.getInstance().refund(viewer, rec.price());

        // Clear the investment
        ccp.setCurrentInvestment(null);
        ccp.setInvestedCoins(0);
        Messages.send(viewer, "<green>Investment cancelled. Refunded $" + String.format("%,d", rec.price()) + "</green>");
        SoundUtils.play(viewer, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);

        refresh();
    }
}

