package me.psikuvit.cashClash.util.game.kc;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.game.DropSpotUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Validates whether a player is standing inside a Kill Confirm capture zone, and resolves a
 * safe spawn location for a zone's 3x3 footprint (see {@link #findSafeCenter}).
 * The zone is a 3x3 block footprint centered on the spawn location (a square, not the
 * circular radius CTF's flag pickup uses), so this checks per-axis distance instead of
 * straight-line distance.
 */
public final class KCZoneValidator {

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

        double halfWidth = CashClashPlugin.getInstance().getConfigManager().getKCZoneHalfWidth();
        double verticalTolerance = CashClashPlugin.getInstance().getConfigManager().getKCZoneVerticalTolerance();
        return dx <= halfWidth && dz <= halfWidth && dy <= verticalTolerance;
    }

    /**
     * Where a zone spawns for a death location: dropped to the ground and moved out of any wall,
     * within {@code zone-ground-search-max-depth} and {@code zone-safe-spawn-search-radius}
     * (see {@link DropSpotUtils#findSafeSpot}).
     */
    public static Location findSafeCenter(Location deathLoc) {
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        return DropSpotUtils.findSafeSpot(deathLoc, cfg.getKCZoneGroundSearchMaxDepth(), cfg.getKCZoneSafeSpawnSearchRadius());
    }
}
