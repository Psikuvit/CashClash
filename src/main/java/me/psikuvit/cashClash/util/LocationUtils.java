package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
     * while keeping all other parameters unchanged. For example, X and Z are set
     * to blockX + 0.5 and blockZ + 0.5, respectively.
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
        double cx = bx + 0.5d;
        double cz = bz + 0.5d;
        return new Location(
                loc.getWorld(),
                cx,
                by,
                cz,
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

    /**
     * Calculate position behind player's head for orbit mechanics
     * Used for flag carrying in CTF
     *
     * @param playerLoc player's location
     * @param headHeight height above player (typically 2.0)
     * @param behindDistance distance behind player (typically 0.5)
     * @return location behind player's head
     */
    public static Location getPlayerHeadLoc(Location playerLoc, double headHeight, double behindDistance) {
        float yaw = (float) Math.toRadians(playerLoc.getYaw());
        double x = playerLoc.getX() - Math.sin(yaw) * behindDistance;
        double z = playerLoc.getZ() + Math.cos(yaw) * behindDistance;
        double y = playerLoc.getY() + headHeight;

        return new Location(playerLoc.getWorld(), x, y, z);
    }

    /**
     * Apply transformation to a BlockDisplay using AxisAngle4f rotation
     * Positions the display and rotates it on Y-axis
     *
     * @param display block display entity
     * @param location new location for display
     * @param yaw rotation angle in radians
     */
    public static void applyOrbitTransformation(BlockDisplay display, Location location, float yaw) {
        Transformation transform = new Transformation(
                new Vector3f(0, 0, 0),                 // translation
                new AxisAngle4f(yaw, 0, 1, 0),         // left rotation (Y-axis spin)
                new Vector3f(1, 1, 1),                 // scale
                new AxisAngle4f(0, 0, 1, 0)            // right rotation (none)
        );

        display.setTransformation(transform);
        display.teleport(location);
    }

    /**
     * Calculate next angle for orbit rotation
     * Increments angle and wraps at 2π
     *
     * @param currentAngle current angle in radians
     * @param angularSpeed increment amount
     * @return next angle in radians
     */
    public static double calculateNextOrbitAngle(double currentAngle, double angularSpeed) {
        double newAngle = currentAngle + angularSpeed;
        if (newAngle > Math.PI * 2) {
            newAngle -= Math.PI * 2;
        }
        return newAngle;
    }

    /**
     * Check if a player is within a specific distance of a location (horizontal only)
     *
     * @param playerLoc player's location
     * @param targetLoc target location
     * @param radius horizontal radius to check
     * @return true if player is within radius horizontally
     */
    public static boolean isPlayerNearLocation(Location playerLoc, Location targetLoc, double radius) {
        if (playerLoc == null || targetLoc == null) return false;
        if (playerLoc.getWorld() != targetLoc.getWorld()) return false;

        double dx = playerLoc.getX() - (targetLoc.getX() + 0.5);
        double dz = playerLoc.getZ() - (targetLoc.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dz * dz);

        return distance <= radius;
    }

    /**
     * Check if a player is standing on a specific block location
     *
     * @param playerLoc player's location
     * @param blockLoc block location to check
     * @param tolerance horizontal distance tolerance (typically 0.7)
     * @return true if player is on the block
     */
    public static boolean isPlayerOnBlock(Location playerLoc, Location blockLoc, double tolerance) {
        if (blockLoc == null || playerLoc.getWorld() != blockLoc.getWorld()) return false;

        return playerLoc.distance(blockLoc.clone().add(0.5, 0, 0.5)) <= tolerance;
    }
}
