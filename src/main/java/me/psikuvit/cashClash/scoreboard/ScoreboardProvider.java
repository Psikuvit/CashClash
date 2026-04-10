package me.psikuvit.cashClash.scoreboard;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.scoreboard.context.CTFScoreboardContext;
import me.psikuvit.cashClash.scoreboard.context.ContextType;
import me.psikuvit.cashClash.scoreboard.context.GameScoreboardContext;
import me.psikuvit.cashClash.scoreboard.context.LobbyScoreboardContext;
import me.psikuvit.cashClash.scoreboard.context.PTPScoreboardContext;
import me.psikuvit.cashClash.scoreboard.context.ScoreboardContext;
import org.bukkit.entity.Player;

/**
 * Factory class for selecting and providing appropriate scoreboard contexts
 * Automatically detects player state and returns the correct context
 */
public class ScoreboardProvider {

    private static final LobbyScoreboardContext LOBBY_CONTEXT = new LobbyScoreboardContext();

    /**
     * Get the appropriate scoreboard context for a player
     * Auto-detects if player is in lobby or game, and what gamemode
     *
     * @param player The player to get context for
     * @return The appropriate ScoreboardContext
     */
    public static ScoreboardContext getContext(Player player) {
        // Check if player is in a game
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session == null) {
            // Player is in lobby
            return LOBBY_CONTEXT;
        }

        // Player is in game - detect gamemode
        return getGamemodeContext(session);
    }

    /**
     * Get the scoreboard context based on the gamemode
     *
     * @param session The game session
     * @return The appropriate GameScoreboardContext
     */
    private static GameScoreboardContext getGamemodeContext(GameSession session) {
        if (session.getGamemode() instanceof CaptureTheFlagGamemode) {
            return new CTFScoreboardContext();
        } else if (session.getGamemode() instanceof ProtectThePresidentGamemode) {
            return new PTPScoreboardContext();
        }

        // Default game context if no specific gamemode handler
        return new GameScoreboardContext() {
            @Override
            protected String fillGamemodeSpecificPlaceholders(String line, Player player, GameSession session) {
                return line;
            }
        };
    }

    /**
     * Check if a player's context changed (used to detect lobby ↔ game transitions)
     *
     * @param player The player to check
     * @param previousContext The previous context type
     * @return true if context changed
     */
    public static boolean hasContextChanged(Player player, ContextType previousContext) {
        ScoreboardContext currentContext = getContext(player);

        if (previousContext == null) {
            return true;
        }

        // Check if context type changed
        return previousContext != currentContext.getContextType();
    }
}



