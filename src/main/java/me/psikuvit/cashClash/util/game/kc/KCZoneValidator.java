package me.psikuvit.cashClash.util.game.kc;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
     * If a death location's 3x3 footprint is embedded in solid blocks (e.g. the player died
     * pressed against a wall), search outward ring by ring on the same Y level for the nearest
     * spot with a clear 3x3 footprint and return that instead. Returns the original location
     * unchanged if it's already clear, or if no open spot is found within the configured
     * {@code gamemodes.kill-confirm.zone-safe-spawn-search-radius}.
     */
    public static Location findSafeCenter(Location deathLoc) {
        if (deathLoc == null || deathLoc.getWorld() == null) return deathLoc;
        if (isFootprintClear(deathLoc)) return deathLoc;

        World world = deathLoc.getWorld();
        int baseX = deathLoc.getBlockX();
        int baseY = deathLoc.getBlockY();
        int baseZ = deathLoc.getBlockZ();

        int maxSearchRadius = CashClashPlugin.getInstance().getConfigManager().getKCZoneSafeSpawnSearchRadius();
        for (int radius = 1; radius <= maxSearchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    Location candidate = new Location(world, baseX + dx + 0.5, baseY, baseZ + dz + 0.5,
                            deathLoc.getYaw(), deathLoc.getPitch());
                    if (isFootprintClear(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return deathLoc;
    }

    /**
     * Whether every block in the 3x3 footprint (at both feet and head height) around a
     * candidate center is non-solid, i.e. the zone wouldn't be spawned inside a wall.
     */
    private static boolean isFootprintClear(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block feet = world.getBlockAt(cx + dx, cy, cz + dz);
                Block head = world.getBlockAt(cx + dx, cy + 1, cz + dz);
                if (feet.getType().isSolid() || head.getType().isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }
}
