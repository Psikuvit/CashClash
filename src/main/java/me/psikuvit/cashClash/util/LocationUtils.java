package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Utility helpers for serializing/deserializing and manipulating Bukkit Locations.
 */
public final class LocationUtils {

    private LocationUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Serialize a Location into a FileConfiguration at the provided path.
     */
    public static void serializeLocation(FileConfiguration cfg, String path, Location loc) {
        if (loc == null) {
            cfg.set(path, null);
            return;
        }
        cfg.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : null);
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", loc.getYaw());
        cfg.set(path + ".pitch", loc.getPitch());
    }

    /**
     * Deserialize a Location from a ConfigurationSection previously saved by {@link #serializeLocation}.
     */
    public static Location deserializeLocation(ConfigurationSection section) {
        if (section == null) return null;
        String world = section.getString("world", null);
        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 0);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);
        World w = world != null ? Bukkit.getWorld(world) : null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    /**
     * Return a new Location with the same coordinates/yaw/pitch but in the specified world.
     * Returns null if the source or world is null.
     * The returned Location will be positioned at the center of the corresponding block
     * (i.e. blockX + 0.5, blockY + 0.5, blockZ + 0.5) to avoid placing players on block edges.
     */
    public static Location copyToWorld(Location source, World world) {
        if (source == null || world == null) return null;
        // Use block coordinates and offset by 0.5 to place in the center of the block.
        int bx = source.getBlockX();
        int by = source.getBlockY();
        int bz = source.getBlockZ();
        double cx = bx + 0.5d;
        double cy = by + 0.5d;
        double cz = bz + 0.5d;
        return new Location(
                world,
                cx,
                cy,
                cz,
                source.getYaw(),
                source.getPitch()
        );
    }

    /**
     * Convenience wrapper to adjust a template/other-world Location into the provided target world.
     * Currently this maps X/Y/Z/Yaw/Pitch directly (world coordinate mapping). If you later need
     * more advanced mapping (e.g., offset transforms), update this method.
     *
     * @param source the source Location (may belong to any world)
     * @param targetWorld the desired target World
     * @return a new Location in targetWorld with coordinates copied from source, or null if source or targetWorld is null
     */
    public static Location adjustLocationToWorld(Location source, World targetWorld) {
        return copyToWorld(source, targetWorld);
    }

    /**
     * Clone a location (null-safe).
     */
    public static Location clone(Location loc) {
        return loc == null ? null : loc.clone();
    }
}
