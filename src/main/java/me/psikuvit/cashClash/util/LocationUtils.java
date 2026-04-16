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
     * Creates a new {@link Location} by copying the given {@code source} location's
     * coordinates, pitch, and yaw to a new location in the specified {@code world}.
     * The source location is first normalized to align with the center of its corresponding block.
     *
     * @param source the {@link Location} to copy; must not be null
     * @param world  the {@link World} to which the new location should belong; must not be null
     * @return a new {@link Location} in the specified world, matching the normalized source location,
     *         or null if the source location is null or the normalization process fails
     */
    public static Location copyToWorld(Location source, World world) {
        Location loc = normalizeLocation(source);
        loc.setWorld(world);
        return loc;
    }

    /**
     * Normalizes a given {@link Location} to align it to the center of the corresponding block.
     * This is achieved by adjusting the X and Z coordinates to the middle of the block
     * while keeping all other parameters unchanged.
     *
     * @param loc the {@link Location} to normalize; may be null
     * @return a new {@link Location} object aligned to the center of the block,
     *         or null if the input location or its world is null
     */
    public static Location normalizeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return new Location(
                loc.getWorld(),
                bx,
                by,
                bz,
                loc.getYaw(),
                loc.getPitch()
        );
    }

    /**
     * Clone a location (null-safe).
     */
    public static Location clone(Location loc) {
        return loc == null ? null : loc.clone();
    }
}
