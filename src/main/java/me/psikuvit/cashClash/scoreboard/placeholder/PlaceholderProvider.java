package me.psikuvit.cashClash.scoreboard.placeholder;

import org.bukkit.entity.Player;

/**
 * Interface for providing placeholder values dynamically
 * Allows for flexible, gamemode-specific placeholders
 */
public interface PlaceholderProvider {

    /**
     * Get the value for a placeholder
     *
     * @param placeholder The placeholder name (without braces)
     * @param player The player viewing the scoreboard
     * @return The value to replace the placeholder with, or null if not handled
     */
    String getValue(String placeholder, Player player);

    /**
     * Check if this provider handles a given placeholder
     *
     * @param placeholder The placeholder name (without braces)
     * @return true if this provider can handle the placeholder
     */
    boolean handles(String placeholder);
}

