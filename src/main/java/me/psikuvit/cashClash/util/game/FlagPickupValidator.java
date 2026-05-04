package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.LocationUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Utility class for validating flag pickup and capturing conditions in CTF.
 * Encapsulates positioning and state checking logic.
 */
public class FlagPickupValidator {

    private static final double CIRCLE_RADIUS = 1.5;
    private static final double SCORE_ZONE_RADIUS = 1.5;

    private FlagPickupValidator() {
        throw new AssertionError("Utility class");
    }

    /**
     * Check if a player is within the pickup circle radius of a flag location
     *
     * @param player The player to check
     * @param flagLocation The location of the flag
     * @return True if the player is near the flag
     */
    public static boolean isPlayerNearFlag(Player player, Location flagLocation) {
        if (flagLocation == null) return false;
        return LocationUtils.isPlayerNearLocation(player.getLocation(), flagLocation, CIRCLE_RADIUS);
    }

    /**
     * Check if a player is inside the circular scoring zone around a banner/plate location
     *
     * @param player The player to check
     * @param bannerLocation The location of the banner/scoring zone
     * @return True if the player is in the scoring zone
     */
    public static boolean isPlayerInScoringZone(Player player, Location bannerLocation) {
        return LocationUtils.isPlayerNearLocation(player.getLocation(), bannerLocation, SCORE_ZONE_RADIUS);
    }

    /**
     * Get the circle radius used for flag pickup detection
     *
     * @return The circle radius in blocks
     */
    public static double getPickupCircleRadius() {
        return CIRCLE_RADIUS;
    }

    /**
     * Get the scoring zone radius
     *
     * @return The score zone radius in blocks
     */
    public static double getScoringZoneRadius() {
        return SCORE_ZONE_RADIUS;
    }
}

