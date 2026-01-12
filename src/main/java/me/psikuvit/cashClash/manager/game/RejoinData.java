package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Stores all data needed to restore a player who disconnected during a game.
 *
 * @param playerUuid      The UUID of the disconnected player
 * @param sessionId       The session they were playing in
 * @param teamNumber      The team number (1 or 2)
 * @param kit             The kit they were using
 * @param coins           Their coin balance at disconnect
 * @param lives           Their remaining lives
 * @param killStreak      Their current kill streak
 * @param totalKills      Their total kills in the game
 * @param inventoryContents Their inventory contents (cloned)
 * @param armorContents   Their armor contents (cloned)
 * @param offhandItem     Their offhand item (cloned)
 * @param purchaseHistory Their purchase history
 * @param ownedEnchants   Their owned enchant levels
 * @param disconnectTime  When they disconnected (System.currentTimeMillis())
 */
public record RejoinData(
        UUID playerUuid,
        UUID sessionId,
        int teamNumber,
        Kit kit,
        long coins,
        int lives,
        int killStreak,
        int totalKills,
        ItemStack[] inventoryContents,
        ItemStack[] armorContents,
        ItemStack offhandItem,
        Queue<PurchaseRecord> purchaseHistory,
        Map<EnchantEntry, Integer> ownedEnchants,
        long disconnectTime
) {

    /**
     * Check if this rejoin data has expired.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return true if expired
     */
    public boolean isExpired(int timeoutSeconds) {
        return System.currentTimeMillis() - disconnectTime > (timeoutSeconds * 1000L);
    }

    /**
     * Get how many seconds remaining before this data expires.
     *
     * @param timeoutSeconds The timeout in seconds
     * @return Seconds remaining, or 0 if expired
     */
    public int getSecondsRemaining(int timeoutSeconds) {
        long elapsed = System.currentTimeMillis() - disconnectTime;
        long remaining = (timeoutSeconds * 1000L) - elapsed;
        return Math.max(0, (int) (remaining / 1000L));
    }
}

