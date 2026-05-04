package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Utility class for managing flag banners in Capture The Flag mode.
 * Handles banner spawning, rotation, particle effects, and player attachment.
 */
public class FlagBannerUtils {

    private static final double BANNER_ORBIT_RADIUS = 2.0;
    private static final double BANNER_ANGULAR_SPEED = 0.03;

    private FlagBannerUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Spawn a flag banner (BlockDisplay) at a location
     *
     * @param location The location to spawn the banner at
     * @param color The color of the banner (RED or BLUE)
     * @return The spawned BlockDisplay entity, or null if failed
     */
    public static BlockDisplay spawnFlagBanner(Location location, Color color) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Create banner 2 blocks above the flag plate
        Location bannerLoc = location.clone().add(0, 2, 0);

        // Spawn BlockDisplay entity for the flag
        return location.getWorld().spawn(bannerLoc, BlockDisplay.class, banner -> {
            Material bannerMaterial = color == Color.RED ? Material.RED_BANNER : Material.BLUE_BANNER;
            banner.setBlock(bannerMaterial.createBlockData());
        });
    }

    /**
     * Check if a banner is able to be rotated (not held by player, not dead, etc.)
     *
     * @param banner The banner to check
     * @param location The flag location
     * @param isHeld Whether the flag is currently held
     * @return True if the banner can be rotated
     */
    public static boolean isBannerRotatable(BlockDisplay banner, Location location, boolean isHeld) {
        return banner != null && !banner.isDead() && location != null && !isHeld;
    }

    /**
     * Update banner position and rotation in a perfect circle around the plate
     *
     * @param banner The banner to rotate
     * @param centerPlate The center plate location
     * @param theta The current rotation angle
     * @param newTheta The new rotation angle
     */
    public static void updateBannerTransform(BlockDisplay banner, Location centerPlate, double theta, double newTheta) {
        if (banner == null || centerPlate == null) {
            return;
        }

        // Compute position on circumference
        double centerX = centerPlate.getX();
        double centerY = centerPlate.getY();
        double centerZ = centerPlate.getZ();

        double x = centerX + Math.cos(newTheta) * BANNER_ORBIT_RADIUS;
        double z = centerZ + Math.sin(newTheta) * BANNER_ORBIT_RADIUS;

        // Create rotation using Y-axis formula
        float yaw = (float) (-newTheta + Math.PI / 2);
        Location newLoc = new Location(centerPlate.getWorld(), x, centerY, z);

        LocationUtils.applyOrbitTransformation(banner, newLoc, yaw);
    }

    /**
     * Calculate the next rotation angle for a banner
     *
     * @param currentAngle The current rotation angle
     * @return The next rotation angle
     */
    public static double calculateNextRotationAngle(double currentAngle) {
        return LocationUtils.calculateNextOrbitAngle(currentAngle, BANNER_ANGULAR_SPEED);
    }

    /**
     * Spawn team-colored particles around the banner
     *
     * @param centerPlate The center plate location
     * @param teamNumber The team number (1=Red, 2=Blue, 0=Neutral)
     */
    public static void spawnBannerParticles(Location centerPlate, int teamNumber) {
        Color teamColor;
        if (teamNumber == 0) {
            // Neutral/black particles for stalemate situations
            teamColor = Color.fromBGR(30, 30, 30);
        } else {
            teamColor = teamNumber == 1 ? Color.RED : Color.BLUE;
        }

        for (int i = 0; i < 8; i++) {
            double particleAngle = Math.PI * 2 * i / 8;
            double particleX = BANNER_ORBIT_RADIUS * Math.cos(particleAngle);
            double particleZ = BANNER_ORBIT_RADIUS * Math.sin(particleAngle);
            ParticleUtils.spawnDust(centerPlate.clone().add(particleX, 0.1, particleZ), teamColor, 1, 1, 0);
        }
    }

    /**
     * Move a banner to follow a player (attach to player's head)
     * Uses direct teleport approach for instant rotation with player
     *
     * @param banner The banner to move
     * @param player The player to follow
     * @param onTaskComplete Callback when task completes
     * @return The BukkitTask for banner carrying
     */
    public static BukkitTask createCarryingTask(BlockDisplay banner, Player player, Runnable onTaskComplete) {
        return SchedulerUtils.runTaskTimer(() -> {
            if (!player.isOnline() || banner.isDead()) {
                if (onTaskComplete != null) {
                    onTaskComplete.run();
                }
                return;
            }

            // Position banner directly on player's head and rotate with player
            Location playerLoc = player.getLocation();
            Location bannerLoc = playerLoc.clone().add(0, 2, 0);

            // Directly teleport banner to match player position and rotation
            banner.teleport(bannerLoc);
            banner.setRotation(playerLoc.getYaw(), playerLoc.getPitch());
        }, 0, 1); // Run every tick for instant synchronization
    }

    /**
     * Stop carrying task and move banner back to plate
     *
     * @param banner The banner to move back
     * @param location The location to move the banner to
     * @param carryingTask The carrying task to cancel
     */
    public static void stopCarryingBanner(BlockDisplay banner, Location location, BukkitTask carryingTask) {
        if (carryingTask != null) {
            carryingTask.cancel();
        }

        if (banner == null || banner.isDead() || location == null) {
            return;
        }

        banner.teleport(location);
        Messages.debug("[CTF] Moved banner back to plate at " + location);
    }
}

