package me.psikuvit.cashClash.util.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Finds where something dropped at a death spot should land: on the ground below it, and out of
 * any wall it would otherwise sit in. Used by Kill Confirm tags and dropped CTF flags.
 */
public final class DropSpotUtils {

    private DropSpotUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Drops an airborne location straight down to the ground (up to {@code maxGroundDepth}
     * blocks), then, if a 3x3 footprint there is embedded in solid blocks, searches outward ring
     * by ring on the same Y level, up to {@code searchRadius}, for the nearest clear one. Returns
     * the grounded location unchanged if it's already clear or nothing clear is found in range.
     */
    public static Location findSafeSpot(Location loc, int maxGroundDepth, int searchRadius) {
        if (loc == null || loc.getWorld() == null) return loc;

        World world = loc.getWorld();
        int baseX = loc.getBlockX();
        int baseZ = loc.getBlockZ();

        int groundY = findGroundY(world, baseX, loc.getBlockY(), baseZ, maxGroundDepth);
        Location startingPoint = loc;
        if (groundY != Integer.MIN_VALUE && groundY != loc.getBlockY()) {
            startingPoint = new Location(world, baseX + 0.5, groundY, baseZ + 0.5, loc.getYaw(), loc.getPitch());
        }

        if (isFootprintClear(startingPoint)) return startingPoint;

        int baseY = startingPoint.getBlockY();
        for (int radius = 1; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    Location candidate = new Location(world, baseX + dx + 0.5, baseY, baseZ + dz + 0.5,
                            loc.getYaw(), loc.getPitch());
                    if (isFootprintClear(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return startingPoint;
    }

    /**
     * Whether there's solid ground at most {@code maxDepth} blocks below a location.
     */
    public static boolean hasGroundBelow(Location loc, int maxDepth) {
        if (loc == null || loc.getWorld() == null) return false;
        return findGroundY(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), maxDepth) != Integer.MIN_VALUE;
    }

    /**
     * The first Y at or below {@code startY} with solid ground directly beneath it (where a
     * falling entity would land), scanning at most {@code maxDepth} blocks, or
     * {@link Integer#MIN_VALUE} if there is none in range (e.g. over the void).
     */
    private static int findGroundY(World world, int x, int startY, int z, int maxDepth) {
        int minY = world.getMinHeight();
        int y = startY;
        int scanned = 0;
        while (y > minY && scanned < maxDepth) {
            if (world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                return y;
            }
            y--;
            scanned++;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Whether every block in the 3x3 footprint (at both feet and head height) around a
     * candidate center is non-solid.
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
