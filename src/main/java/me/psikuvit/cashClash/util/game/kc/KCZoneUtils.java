package me.psikuvit.cashClash.util.game.kc;

import me.psikuvit.cashClash.gamemode.impl.KCZone;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;

/**
 * Spawns and despawns the temporary display entities that make up a Kill Confirm capture zone:
 * a flat glowing 3x3 platform plus a nametag/emerald/heart marker floating above it. Mirrors
 * CTF's flag-banner convention (util.game.ctf.FlagBannerUtils) of using BlockDisplay/ItemDisplay
 * entities rather than real placed blocks, so nothing needs to be reverted in the world.
 */
public final class KCZoneUtils {

    private static final Vector3f PLATFORM_SCALE = new Vector3f(3f, 0.15f, 3f);
    private static final Color GOLD_GLOW = Color.fromRGB(255, 215, 0);
    private static final Color GREEN_GLOW = Color.fromRGB(0, 220, 90);

    private KCZoneUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Spawn the platform + marker entities for a zone and attach them to it.
     */
    public static void spawnZoneEntities(KCZone zone) {
        Location center = zone.getCenter();
        if (center == null || center.getWorld() == null) return;

        boolean isNametag = zone.getKind() == KCZone.ZoneKind.NAMETAG;
        Material platformMaterial = isNametag ? Material.GOLD_BLOCK : Material.EMERALD_BLOCK;
        Color glowColor = isNametag ? GOLD_GLOW : GREEN_GLOW;

        BlockDisplay platform = center.getWorld().spawn(center, BlockDisplay.class, display -> {
            display.setBlock(platformMaterial.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(-1.5f, -0.05f, -1.5f),
                    new AxisAngle4f(0, 0, 0, 1),
                    PLATFORM_SCALE,
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            display.setGlowing(true);
            display.setGlowColorOverride(glowColor);
            display.setBrightness(new Display.Brightness(15, 15));
        });
        zone.setPlatformDisplay(platform);

        Location iconLoc = center.clone().add(0, 1.2, 0);
        Entity icon = switch (zone.getKind()) {
            case NAMETAG -> spawnNametag(iconLoc, zone.getVictimName());
            case MONEY -> spawnIconItem(iconLoc, Material.EMERALD);
            case HEART -> spawnIconItem(iconLoc, Material.TOTEM_OF_UNDYING);
        };
        zone.setIconDisplay(icon);
    }

    private static TextDisplay spawnNametag(Location iconLoc, String victimName) {
        return iconLoc.getWorld().spawn(iconLoc, TextDisplay.class, display -> {
            display.text(Messages.parse("<gold><bold>" + victimName + "'s Tag</bold></gold>"));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setSeeThrough(true);
            display.setShadowed(false);
        });
    }

    private static ItemDisplay spawnIconItem(Location iconLoc, Material material) {
        return iconLoc.getWorld().spawn(iconLoc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(material));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            Transformation t = display.getTransformation();
            display.setTransformation(new Transformation(
                    t.getTranslation(),
                    t.getLeftRotation(),
                    new Vector3f(1.5f, 1.5f, 1.5f),
                    t.getRightRotation()
            ));
        });
    }

    /**
     * Remove a zone's platform and marker entities.
     */
    public static void despawnZoneEntities(KCZone zone) {
        if (zone == null) return;
        if (zone.getPlatformDisplay() != null && !zone.getPlatformDisplay().isDead()) {
            zone.getPlatformDisplay().remove();
        }
        if (zone.getIconDisplay() != null && !zone.getIconDisplay().isDead()) {
            zone.getIconDisplay().remove();
        }
    }

    /**
     * Despawn every zone in the given collection (round-end/game-end cleanup).
     */
    public static void cleanupAllZones(Collection<KCZone> zones) {
        for (KCZone zone : zones) {
            despawnZoneEntities(zone);
        }
    }

    /**
     * A brief green pulse shown while a player is actively holding a zone.
     */
    public static void spawnCaptureProgressParticles(Location center) {
        if (center == null || center.getWorld() == null) return;
        ParticleUtils.circle(Particle.HAPPY_VILLAGER, center.clone().add(0, 0.2, 0), 1.5, 0.0, 8, 0);
    }
}
