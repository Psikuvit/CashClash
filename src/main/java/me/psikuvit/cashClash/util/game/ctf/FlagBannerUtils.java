package me.psikuvit.cashClash.util.game.ctf;

import me.psikuvit.cashClash.gamemode.impl.FlagState;
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

import java.util.HashMap;
import java.util.Map;

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
      * @param newTheta The new rotation angle
      */
     public static void updateBannerTransform(BlockDisplay banner, Location centerPlate, double newTheta) {
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
         Location newLoc = new Location(centerPlate.getWorld(), x, banner.getY(), z);

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
     * Spawn team-colored particles around the banner.
     *
     * @param centerPlate The center plate location
     * @param teamNumber The team number (1=Red, 2=Blue, 0=Neutral)
     */
     public static void spawnBannerParticles(Location centerPlate, int teamNumber) {
        Color teamColor = teamNumber == 0 ? Color.fromBGR(30, 30, 30) :
                (teamNumber == 1 ? Color.RED : Color.BLUE);

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
            if (!player.isOnline() || banner.isDead() || player.isDead()) {
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

     /**
      * Initialize both team banners at their base locations.
      * Returns a map containing base locations and a callback to update flag states.
      *
      * @param redFlagLoc Red flag location
      * @param blueFlagLoc Blue flag location
      * @return Map with initialized flag states, or empty map if initialization fails
      */
     public static Map<Integer, FlagState> initializeFlagBanners(Location redFlagLoc, Location blueFlagLoc) {
         Map<Integer, FlagState> flagStates = new HashMap<>();

         // Red flag (Team 1)
         if (redFlagLoc != null) {
             BlockDisplay redBanner = spawnFlagBanner(redFlagLoc, Color.RED);
             if (redBanner != null) {
                 flagStates.put(1, new FlagState(null, 0, redFlagLoc, redBanner, 0.0, null, 0.0, 0L, 3));
                 Messages.debug("[CTF] Spawned Red flag banner at " + redFlagLoc);
             }
         }

         // Blue flag (Team 2)
         if (blueFlagLoc != null) {
             BlockDisplay blueBanner = spawnFlagBanner(blueFlagLoc, Color.BLUE);
             if (blueBanner != null) {
                 flagStates.put(2, new FlagState(null, 0, blueFlagLoc, blueBanner, 0.0, null, 0.0, 0L, 3));
                 Messages.debug("[CTF] Spawned Blue flag banner at " + blueFlagLoc);
             }
         }

         return flagStates;
     }

     /**
      * Rotate a specific team's banner based on its current state
      *
      * @param flag The flag state containing banner info
      * @param centerPlate The center plate location for rotation orbit
      * @return Updated FlagState with new rotation angle
      */
     public static FlagState rotateBanner(FlagState flag, Location centerPlate) {
         if (flag == null || !isBannerRotatable(flag.bannerDisplay(), centerPlate, flag.isHeld())) {
             return flag;
         }

         double newTheta = calculateNextRotationAngle(flag.bannerAngle());
         updateBannerTransform(flag.bannerDisplay(), centerPlate, newTheta);

         return flag.withBannerAngle(newTheta);
     }

     /**
      * Remove all banners from players and return them to their base plates.
      * Utility method to clean up banners at end of round.
      *
      * @param flagStates The current flag states
      * @param taskCanceller Function to cancel a task
      */
     public static void removeAllBannersFromPlayers(Map<Integer, FlagState> flagStates, java.util.function.Consumer<BukkitTask> taskCanceller) {
         for (FlagState flag : flagStates.values()) {
             if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                 // Stop carrying task if active
                 if (flag.carryingTask() != null) {
                     taskCanceller.accept(flag.carryingTask());
                 }

                 if (!flag.isHeld()) {
                     continue; // If not held, banner should already be at plate
                 }

                 // Move banner back to its plate
                 if (flag.getFlagLoc() != null) {
                     stopCarryingBanner(flag.bannerDisplay(), flag.getFlagLoc(), flag.carryingTask());
                 }
             }
         }
     }

     /**
      * Clean up all banner entities
      *
      * @param flagStates The flag states containing banners
      */
     public static void cleanupAllBanners(Map<Integer, FlagState> flagStates) {
         for (FlagState flag : flagStates.values()) {
             if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                 flag.bannerDisplay().remove();
             }
         }
     }
}

