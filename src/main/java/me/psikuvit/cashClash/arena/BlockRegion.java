package me.psikuvit.cashClash.arena;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

/**
 * A box of whole blocks with both corners included - a spawn room or one of its doors. Stored in
 * template coordinates, which a game's world copy shares.
 */
public record BlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static BlockRegion of(Location a, Location b) {
        return new BlockRegion(
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location loc) {
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * The space the region's blocks fill.
     */
    public BoundingBox toBoundingBox() {
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public double centerDistanceSquared(Location loc) {
        double dx = (minX + maxX + 1) / 2.0 - loc.getX();
        double dy = (minY + maxY + 1) / 2.0 - loc.getY();
        double dz = (minZ + maxZ + 1) / 2.0 - loc.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * {@code "minX minY minZ maxX maxY maxZ"}, as templates store it.
     */
    public String serialize() {
        return minX + " " + minY + " " + minZ + " " + maxX + " " + maxY + " " + maxZ;
    }

    /**
     * @return the region, or null when {@code text} isn't six whole numbers
     */
    public static BlockRegion deserialize(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 6) return null;
        try {
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(parts[i]);
            return new BlockRegion(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                    Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
