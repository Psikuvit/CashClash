package me.psikuvit.cashClash.util.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.game.GameSessionUtils;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Common CashClashPlayer wrapper retrieval patterns to eliminate duplicate
 * null checks across event handlers and command classes.
 */
public class PlayerUtils {
    private PlayerUtils() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Get a CashClashPlayer wrapper from session, or null if not found.
     * Safe to call with null session.
     */
    public static CashClashPlayer getOrNull(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return null;
        return session.getCashClashPlayer(playerUuid);
    }

    /**
     * Get CashClashPlayer for an online Player, or null if not in a game.
     */
    public static CashClashPlayer getOrNull(Player player) {
        if (player == null) return null;
        GameSession session = GameSessionUtils.getSessionOrNull(player);
        return getOrNull(session, player.getUniqueId());
    }

    /**
     * Check if a player exists in their session wrapper.
     * Useful for early returns in event handlers.
     */
    public static boolean exists(GameSession session, UUID playerUuid) {
        return getOrNull(session, playerUuid) != null;
    }
}
