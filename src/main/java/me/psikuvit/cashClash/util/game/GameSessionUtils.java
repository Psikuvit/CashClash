package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import org.bukkit.entity.Player;

/**
 * Common GameSession retrieval patterns to eliminate duplicate null checks
 * across listeners and managers.
 */
public class GameSessionUtils {
    private GameSessionUtils() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Retrieve a player's active game session, or null if not in a game.
     * Use this as a safe way to get a session without repeated manager access.
     */
    public static GameSession getSessionOrNull(Player player) {
        if (player == null) return null;
        return CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
    }
}
