package me.psikuvit.cashClash.util.game.kc;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Validates whether a player is standing inside a Kill Confirm capture zone.
 * The zone is a 3x3 block footprint centered on the spawn location (a square, not the
 * circular radius CTF's flag pickup uses), so this checks per-axis distance instead of
 * straight-line distance.
 */
public final class KCZoneValidator {

    private static final double HALF_WIDTH = 1.5;
    private static final double VERTICAL_TOLERANCE = 2.0;

    private KCZoneValidator() {
        throw new AssertionError("Utility class");
    }

    public static boolean isPlayerInZone(Player player, Location zoneCenter) {
        if (player == null || zoneCenter == null) return false;
        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld() != zoneCenter.getWorld()) return false;

        double dx = Math.abs(playerLoc.getX() - zoneCenter.getX());
        double dz = Math.abs(playerLoc.getZ() - zoneCenter.getZ());
        double dy = Math.abs(playerLoc.getY() - zoneCenter.getY());

        return dx <= HALF_WIDTH && dz <= HALF_WIDTH && dy <= VERTICAL_TOLERANCE;
    }
}
