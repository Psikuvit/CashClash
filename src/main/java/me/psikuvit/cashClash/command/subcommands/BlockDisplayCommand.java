package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Location;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Command to edit block displays with full control over block type and transformations
 * Usage: /cc blockdisplay <target|setblock|teleport|translate|rotate|scale|reset|list>
 */
public class BlockDisplayCommand extends AbstractArgCommand {

    // Stores the last targeted BlockDisplay for each player
    private static class SessionData {
        BlockDisplay targetDisplay;

        BukkitTask orbitTask;
        double orbitRadius;
        double orbitHeight;
        double orbitAngle;
    }

    private final Map<UUID, SessionData> playerSessions = new HashMap<>();

    public BlockDisplayCommand() {
        super("blockdisplay", List.of("bd", "bdisplay"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "blockdisplay.only-players");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        try {
            switch (action) {
                case "target" -> targetBlockDisplay(player, args);
                case "setblock" -> setBlockType(player, args);
                case "teleport", "tp" -> teleportDisplay(player, args);
                case "translate" -> translateDisplay(player, args);
                case "rotate" -> rotateDisplay(player, args);
                case "scale" -> scaleDisplay(player, args);
                case "reset" -> resetDisplay(player);
                case "list" -> listNearbyDisplays(player, args);
                case "info" -> showDisplayInfo(player);
                case "orbit" -> orbitAroundPlayer(player, args);
                case "passenger" -> addAsPassenger(player, args);
                default -> sendHelp(player);
            }
        } catch (Exception e) {
            Messages.send(player, "blockdisplay.error", "{error_msg}", e.getMessage());
            return true;
        }

        return true;
    }

    /**
     * Target the nearest BlockDisplay or look for one by index
     */
    private void targetBlockDisplay(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "blockdisplay.target-usage");
            return;
        }

        SessionData session = playerSessions.computeIfAbsent(player.getUniqueId(), k -> new SessionData());
        String targetType = args[1].toLowerCase(Locale.ROOT);

        if ("nearest".equals(targetType)) {
            BlockDisplay nearest = findNearestBlockDisplay(player, 50);
            if (nearest != null) {
                session.targetDisplay = nearest;
                Messages.send(player, "blockdisplay.targeted-by-location", "{location}", formatLocation(nearest.getLocation()));
            } else {
                Messages.send(player, "blockdisplay.no-blockdisplay-found");
            }
        } else {
            try {
                int index = Integer.parseInt(targetType);
                List<BlockDisplay> displays = getNearbyBlockDisplays(player, 50);
                if (index >= 0 && index < displays.size()) {
                    session.targetDisplay = displays.get(index);
                    Messages.send(player, "blockdisplay.targeted-by-index", "{index}", String.valueOf(index));
                } else {
                    Messages.send(player, "blockdisplay.index-out-of-range");
                }
            } catch (NumberFormatException e) {
                Messages.send(player, "blockdisplay.invalid-index");
            }
        }
    }

    /**
     * Set the block type of the targeted BlockDisplay
     */
    private void setBlockType(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "blockdisplay.setblock-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        String materialName = args[1].toUpperCase(Locale.ROOT);
        try {
            Material material = Material.valueOf(materialName);
            if (!material.isBlock()) {
                Messages.send(player, "blockdisplay.invalid-material", "{material_name}", materialName);
                return;
            }
            display.setBlock(org.bukkit.Bukkit.createBlockData(material));
            Messages.send(player, "blockdisplay.block-set", "{material}", material.toString());
        } catch (IllegalArgumentException e) {
            Messages.send(player, "blockdisplay.unknown-material", "{material_name}", materialName);
        }
    }

    /**
     * Teleport the display to a specific location (player location, or coordinates)
     */
    private void teleportDisplay(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "blockdisplay.teleport-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        Location newLoc;

        if ("player".equalsIgnoreCase(args[1])) {
            newLoc = player.getLocation();
        } else {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                newLoc = new Location(player.getWorld(), x, y, z);

                if (args.length >= 5) {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(args[4]);
                    if (world != null) {
                        newLoc.setWorld(world);
                    }
                }
            } catch (NumberFormatException e) {
                Messages.send(player, "blockdisplay.invalid-coordinates");
                return;
            }
        }

        display.teleport(newLoc);
        Messages.send(player, "blockdisplay.teleported", "{location}", formatLocation(newLoc));
    }

    /**
     * Translate (move) the display relative to its current position
     */
    private void translateDisplay(Player player, String[] args) {
        if (args.length < 4) {
            Messages.send(player, "blockdisplay.translate-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        try {
            double dx = Double.parseDouble(args[1]);
            double dy = Double.parseDouble(args[2]);
            double dz = Double.parseDouble(args[3]);

            Location current = display.getLocation();
            Location newLoc = current.clone().add(dx, dy, dz);
            display.teleport(newLoc);

            Messages.send(player, "blockdisplay.translated", "{dx}", String.valueOf(dx), "{dy}", String.valueOf(dy), "{dz}", String.valueOf(dz));
        } catch (NumberFormatException e) {
            Messages.send(player, "blockdisplay.invalid-translation");
        }
    }

    /**
     * Rotate the display (pitch, yaw, roll in degrees)
     * Usage: /cc blockdisplay rotate <axis> <degrees> or /cc blockdisplay rotate <pitch> <yaw> <roll>
     */
    private void rotateDisplay(Player player, String[] args) {
        if (args.length < 3) {
            Messages.send(player, "blockdisplay.rotate-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        try {
            Transformation current = display.getTransformation();

            // If 3 arguments after 'rotate', treat as pitch yaw roll
            if (args.length >= 4 && !isAxis(args[1])) {
                float pitch = (float) Math.toRadians(Double.parseDouble(args[1]));
                float yaw = (float) Math.toRadians(Double.parseDouble(args[2]));
                float roll = (float) Math.toRadians(Double.parseDouble(args[3]));

                 Quaternionf newRotation = eulerToQuaternion(pitch, yaw, roll);
                 Transformation newTransform = new Transformation(
                         current.getTranslation(),
                         newRotation,
                         current.getScale(),
                         current.getRightRotation()
                 );
                 display.setTransformation(newTransform);
                 Messages.send(player, "blockdisplay.rotated-euler", "{pitch}", args[1], "{yaw}", args[2], "{roll}", args[3]);
             } else {
                 // Single axis rotation
                 String axis = args[1].toLowerCase(Locale.ROOT);
                 float degrees = (float) Double.parseDouble(args[2]);
                 float radians = (float) Math.toRadians(degrees);

                 Quaternionf newRotation = switch (axis) {
                     case "x", "pitch" -> new Quaternionf().rotateX(radians);
                     case "y", "yaw" -> new Quaternionf().rotateY(radians);
                     case "z", "roll" -> new Quaternionf().rotateZ(radians);
                     default -> null;
                 };

                if (newRotation == null) {
                    Messages.send(player, "blockdisplay.invalid-axis");
                    return;
                }

                Transformation newTransform = new Transformation(
                        current.getTranslation(),
                        newRotation,
                        current.getScale(),
                        current.getRightRotation()
                );
                display.setTransformation(newTransform);
                Messages.send(player, "blockdisplay.rotated-axis", "{axis}", axis, "{degrees}", String.valueOf(degrees));
            }
        } catch (NumberFormatException e) {
            Messages.send(player, "blockdisplay.invalid-rotation");
        }
    }

    /**
     * Scale the display (uniform or per-axis)
     * Usage: /cc blockdisplay scale <factor> or /cc blockdisplay scale <sx> <sy> <sz>
     */
    private void scaleDisplay(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "blockdisplay.scale-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        try {
            Transformation current = display.getTransformation();
            Vector3f newScale;

            if (args.length == 2) {
                float factor = Float.parseFloat(args[1]);
                newScale = new Vector3f(factor, factor, factor);
                Messages.send(player, "blockdisplay.scaled-uniform", "{factor}", String.valueOf(factor));
            } else if (args.length >= 4) {
                float sx = Float.parseFloat(args[1]);
                float sy = Float.parseFloat(args[2]);
                float sz = Float.parseFloat(args[3]);
                newScale = new Vector3f(sx, sy, sz);
                Messages.send(player, "blockdisplay.scaled", "{sx}", String.valueOf(sx), "{sy}", String.valueOf(sy), "{sz}", String.valueOf(sz));
            } else {
                Messages.send(player, "blockdisplay.invalid-scale-args");
                return;
            }

            Transformation newTransform = new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    newScale,
                    current.getRightRotation()
            );
            display.setTransformation(newTransform);
        } catch (NumberFormatException e) {
            Messages.send(player, "blockdisplay.invalid-scale");
        }
    }

    /**
     * Reset the display to default transformation (identity)
     */
    private void resetDisplay(Player player) {
        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        Transformation identity = new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(0, 0, 0, 1),
                new Vector3f(1, 1, 1),
                new Quaternionf(0, 0, 0, 1)
        );
        display.setTransformation(identity);
        Messages.send(player, "blockdisplay.reset");
    }

    /**
     * List all nearby BlockDisplays within range
     */
    private void listNearbyDisplays(Player player, String[] args) {
        int range = 50;
        if (args.length >= 2) {
            try {
                range = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        List<BlockDisplay> displays = getNearbyBlockDisplays(player, range);
        if (displays.isEmpty()) {
            Messages.send(player, "blockdisplay.no-displays-found", "{range}", String.valueOf(range));
            return;
        }

        Messages.send(player, "blockdisplay.list-title", "{range}", String.valueOf(range));
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay bd = displays.get(i);
            Messages.send(player, "blockdisplay.list-item", "{index}", String.valueOf(i), "{location}", formatLocation(bd.getLocation()), "{material}", bd.getBlock().getMaterial().toString());
        }
    }

     /**
     * Show detailed info about the targeted display
     */
    private void showDisplayInfo(Player player) {
        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        Transformation transform = display.getTransformation();
        Vector3f translation = transform.getTranslation();
        Vector3f scale = transform.getScale();
        Quaternionf rotation = transform.getLeftRotation();

        Messages.send(player, "blockdisplay.info-title");
        Messages.send(player, "blockdisplay.info-location", "{location}", formatLocation(display.getLocation()));
        Messages.send(player, "blockdisplay.info-material", "{material}", display.getBlock().getMaterial().toString());
        Messages.send(player, "blockdisplay.info-translation", "{x}", String.valueOf(translation.x), "{y}", String.valueOf(translation.y), "{z}", String.valueOf(translation.z));
        Messages.send(player, "blockdisplay.info-scale", "{x}", String.valueOf(scale.x), "{y}", String.valueOf(scale.y), "{z}", String.valueOf(scale.z));
        Messages.send(player, "blockdisplay.info-rotation", "{x}", String.valueOf(rotation.x), "{y}", String.valueOf(rotation.y), "{z}", String.valueOf(rotation.z), "{w}", String.valueOf(rotation.w));
        Messages.send(player, "blockdisplay.info-entity-id", "{entity_id}", String.valueOf(display.getEntityId()));
    }

    /**
     * Orbit the targeted BlockDisplay around the player in a circular path
     * Usage: /cc blockdisplay orbit <radius> [height_offset]
     */
    private void orbitAroundPlayer(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "blockdisplay.orbit-usage");
            return;
        }

        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        try {
            double radius = Double.parseDouble(args[1]);
            double heightOffset = 0;
            if (args.length >= 3) {
                heightOffset = Double.parseDouble(args[2]);
            }

            // Ensure session exists
            SessionData session = playerSessions.computeIfAbsent(player.getUniqueId(), k -> new SessionData());

            // Stop existing orbit if active
            if (session.orbitTask != null) {
                session.orbitTask.cancel();
                Messages.send(player, "blockdisplay.orbit-stopped");
            }

            // Reset orbit angle for new orbit
            session.orbitRadius = radius;
            session.orbitHeight = heightOffset;
            session.orbitAngle = 0;

            // Start new orbit task - runs every tick (20 times per second)
            session.orbitTask = SchedulerUtils.runTaskTimer(() -> {
                if (!player.isOnline() || display.isDead()) {
                    if (session.orbitTask != null) {
                        session.orbitTask.cancel();
                        session.orbitTask = null;
                    }
                    return;
                }

                // Increment angle for smooth rotation (about 360 degrees per 5 seconds)
                session.orbitAngle += 0.015;
                if (session.orbitAngle > Math.PI * 2) {
                    session.orbitAngle -= Math.PI * 2;
                }

                Location playerLoc = player.getLocation();
                double x = playerLoc.getX() + Math.cos(session.orbitAngle) * session.orbitRadius;
                double z = playerLoc.getZ() + Math.sin(session.orbitAngle) * session.orbitRadius;
                double y = playerLoc.getY() + session.orbitHeight;

                Location orbitLoc = new Location(playerLoc.getWorld(), x, y, z);

                Transformation transformation = getTransformation(session);

                display.setTransformation(transformation);
                display.teleport(orbitLoc);
            }, 0, 1);

            Messages.send(player, "blockdisplay.orbit-activated", "{radius}", String.valueOf(radius), "{height}", String.valueOf(heightOffset));
        } catch (NumberFormatException e) {
            Messages.send(player, "blockdisplay.invalid-orbit-args");
        }
    }

    private static @NonNull Transformation getTransformation(SessionData session) {
        float yaw = (float) (-session.orbitAngle + Math.PI / 2);

        return new Transformation(
                new Vector3f(0, 0, 0),                          // translation
                new AxisAngle4f(yaw, 0, 1, 0),                  // left rotation (Y-axis spin)
                new Vector3f(1, 1, 1),                          // scale
                new AxisAngle4f(0, 0, 1, 0)                     // right rotation (none)
        );
    }

    /**
     * Add the targeted BlockDisplay as a passenger to the player
     * This makes the display float above the player and move with them
     * Usage: /cc blockdisplay passenger
     */
    private void addAsPassenger(Player player, String[] args) {
        BlockDisplay display = getTargetDisplay(player);
        if (display == null) return;

        try {
            // If already a passenger of this player, remove it
            if (display.getVehicle() == player) {
                player.removePassenger(display);
                Messages.send(player, "blockdisplay.passenger-removed");
                return;
            }

            // Remove from any other vehicle
            if (display.getVehicle() != null) {
                display.getVehicle().removePassenger(display);
            }

            // Add as passenger to player
            player.addPassenger(display);
            Messages.send(player, "blockdisplay.passenger-added");
        } catch (Exception e) {
            Messages.send(player, "blockdisplay.error", "{error_msg}", e.getMessage());
        }
    }

    /**
     *Helper: Get or prompt for target display
     */
    private BlockDisplay getTargetDisplay(Player player) {
        SessionData session = playerSessions.get(player.getUniqueId());
        if (session == null || session.targetDisplay == null || session.targetDisplay.isDead()) {
            Messages.send(player, "blockdisplay.no-target");
            return null;
        }
        return session.targetDisplay;
    }

    /**
     * Helper: Find nearest BlockDisplay
     */
    private BlockDisplay findNearestBlockDisplay(Player player, int range) {
        List<BlockDisplay> displays = getNearbyBlockDisplays(player, range);
        if (displays.isEmpty()) return null;

        BlockDisplay nearest = displays.getFirst();
        double nearestDist = player.getLocation().distance(nearest.getLocation());

        for (BlockDisplay bd : displays) {
            double dist = player.getLocation().distance(bd.getLocation());
            if (dist < nearestDist) {
                nearest = bd;
                nearestDist = dist;
            }
        }

        return nearest;
    }

    /**
     * Helper: Get all nearby BlockDisplays
     */
    private List<BlockDisplay> getNearbyBlockDisplays(Player player, int range) {
        List<BlockDisplay> displays = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof BlockDisplay bd) {
                displays.add(bd);
            }
        }
        return displays;
    }

    /**
     * Helper: Check if string is a rotation axis
     */
    private boolean isAxis(String str) {
        String lower = str.toLowerCase(Locale.ROOT);
        return lower.equals("x") || lower.equals("y") || lower.equals("z") ||
               lower.equals("pitch") || lower.equals("yaw") || lower.equals("roll");
    }

    /**
     * Convert Euler angles (pitch, yaw, roll) to Quaternionf
     */
    private Quaternionf eulerToQuaternion(float pitch, float yaw, float roll) {
        // Create rotation matrices and combine them
        // This is a simplified conversion; for accurate results, use quaternion math
        Quaternionf q = new Quaternionf();
        q.rotateXYZ(pitch, yaw, roll);
        return q;
    }

    private void sendHelp(Player player) {
        Messages.send(player, "blockdisplay.help-title");
        Messages.send(player, "blockdisplay.help-target");
        Messages.send(player, "blockdisplay.help-setblock");
        Messages.send(player, "blockdisplay.help-teleport");
        Messages.send(player, "blockdisplay.help-translate");
        Messages.send(player, "blockdisplay.help-rotate-axis");
        Messages.send(player, "blockdisplay.help-rotate-euler");
        Messages.send(player, "blockdisplay.help-scale-uniform");
        Messages.send(player, "blockdisplay.help-scale-axes");
        Messages.send(player, "blockdisplay.help-reset");
        Messages.send(player, "blockdisplay.help-list");
        Messages.send(player, "blockdisplay.help-info");
        Messages.send(player, "blockdisplay.help-orbit");
        Messages.send(player, "blockdisplay.help-passenger");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("target", "setblock", "teleport", "translate", "rotate", "scale", "reset", "list", "info", "orbit", "passenger");
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            return switch (action) {
                case "target" -> List.of("nearest", "0", "1", "2");
                case "setblock" -> getMaterials();
                case "teleport" -> List.of("player", "0", "64", "128");
                case "rotate" -> List.of("x", "y", "z", "pitch", "yaw", "roll");
                case "orbit" -> List.of("1", "2", "3", "5");
                default -> List.of();
            };
        }

        return List.of();
    }

    private List<String> getMaterials() {
        List<String> materials = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                materials.add(m.toString().toLowerCase(Locale.ROOT));
            }
        }
        return materials.stream().limit(50).toList();
    }

    /**
     * Format a location for display
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
    }
}







