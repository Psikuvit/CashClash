package me.psikuvit.cashClash.scoreboard.context;

import me.psikuvit.cashClash.game.GameSession;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a scoreboard context for a specific player state (lobby or game).
 * Contexts handle lines, titles, and placeholder filling based on the current state.
 */
public interface ScoreboardContext {

    /**
     * Get the scoreboard title
     */
    Component getTitle(Player player, GameSession session);

    /**
     * Get scoreboard lines for this context
     */
    List<String> getLines(Player player, GameSession session);

    /**
     * Fill all placeholders in a line with actual data
     */
    String fillPlaceholders(String line, Player player, GameSession session);

    /**
     * Check if this context should update (performance optimization)
     */
    default boolean shouldUpdate(Player player, GameSession session) {
        return true; // Update every tick by default
    }

    /**
     * Get context type
     */
    ContextType getContextType();
}

