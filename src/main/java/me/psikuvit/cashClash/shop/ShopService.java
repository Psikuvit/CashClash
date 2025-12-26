package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;

/**
 * Centralized service for shop purchase logic.
 * Handles validation, balance checks, and purchase/refund operations.
 */
public class ShopService {

    private static ShopService instance;

    private ShopService() {}

    public static ShopService getInstance() {
        if (instance == null) {
            instance = new ShopService();
        }
        return instance;
    }

    // ==================== PURCHASE VALIDATION ====================

    /**
     * Check if a player can afford an item.
     * @return true if player has enough coins
     */
    public boolean canAfford(Player player, Purchasable item) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return false;

        return ccp.getCoins() >= item.getPrice();
    }

    /**
     * Check if a player can afford a specific price.
     */
    public boolean canAfford(Player player, long price) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return false;

        return ccp.getCoins() >= price;
    }

    /**
     * Validate if a purchase can proceed.
     * @return PurchaseResult with status and message
     */
    public PurchaseResult validatePurchase(Player player, Purchasable item) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            return PurchaseResult.failure("You must be in a game!");
        }

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) {
            return PurchaseResult.failure("Player data not found!");
        }

        if (ccp.getCoins() < item.getPrice()) {
            long needed = item.getPrice() - ccp.getCoins();
            return PurchaseResult.failure("Not enough coins! Need $" + String.format("%,d", needed) + " more.");
        }

        return PurchaseResult.success();
    }

    // ==================== PURCHASE OPERATIONS ====================

    /**
     * Execute a purchase (deduct coins).
     * @return true if successful
     */
    public boolean purchase(Player player, Purchasable item) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return false;

        if (ccp.getCoins() < item.getPrice()) {
            return false;
        }

        ccp.deductCoins(item.getPrice());
        return true;
    }

    /**
     * Execute a purchase with custom price.
     */
    public boolean purchase(Player player, long price) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return false;

        if (ccp.getCoins() < price) {
            return false;
        }

        ccp.deductCoins(price);
        return true;
    }

    /**
     * Refund an item (return coins).
     */
    public void refund(Player player, Purchasable item) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        ccp.addCoins(item.getPrice());
    }

    /**
     * Refund a custom price.
     */
    public void refund(Player player, long price) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        ccp.addCoins(price);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get player's current balance.
     */
    public long getBalance(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return 0;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        return ccp != null ? ccp.getCoins() : 0;
    }

    /**
     * Send formatted purchase message.
     */
    public void sendPurchaseMessage(Player player, Purchasable item) {
        Messages.send(player, "<green>Purchased " + item.getDisplayName() +
                " for <gold>$" + String.format("%,d", item.getPrice()) + "</gold></green>");
    }

    /**
     * Send formatted refund message.
     */
    public void sendRefundMessage(Player player, Purchasable item) {
        Messages.send(player, "<yellow>Refunded " + item.getDisplayName() +
                " for <gold>$" + String.format("%,d", item.getPrice()) + "</gold></yellow>");
    }

    // ==================== RESULT CLASS ====================

    /**
     * Result of a purchase validation.
     */
    public static class PurchaseResult {
        private final boolean success;
        private final String message;

        private PurchaseResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static PurchaseResult success() {
            return new PurchaseResult(true, null);
        }

        public static PurchaseResult failure(String message) {
            return new PurchaseResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}

