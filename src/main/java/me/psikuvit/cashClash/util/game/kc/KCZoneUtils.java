package me.psikuvit.cashClash.util.game.kc;

import me.psikuvit.cashClash.gamemode.impl.KCZone;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * a flat glowing 3x3 platform, a nametag/emerald/heart marker floating above it, and a countdown
 * timer above that. Mirrors CTF's flag-banner convention (util.game.ctf.FlagBannerUtils) of using
 * BlockDisplay/ItemDisplay entities rather than real placed blocks, so nothing needs to be
 * reverted in the world.
 */
public final class KCZoneUtils {

    private static final Vector3f PLATFORM_SCALE = new Vector3f(3f, 0.15f, 3f);
    private static final Color GOLD_GLOW = Color.fromRGB(255, 215, 0);
    private static final Color GREEN_GLOW = Color.fromRGB(0, 220, 90);
    private static final Color RED_GLOW = Color.fromRGB(220, 40, 40);

    // Dim/uncolored look used for the first ZONE_ACTIVATION_DELAY_MS while a zone is still
    // "pending" (spawned but not yet capturable), swapped for ACTIVE_BRIGHTNESS + the kind's
    // glow color once it lights up.
    private static final Display.Brightness PENDING_BRIGHTNESS = new Display.Brightness(3, 3);
    private static final Display.Brightness ACTIVE_BRIGHTNESS = new Display.Brightness(15, 15);

    // Sentinel stored in KCZone#lastDisplayedTimerSeconds while showing "Contested!" instead of
    // a countdown number - distinct from the -1 "not yet displayed" default and any real second count.
    private static final int CONTESTED_SENTINEL = -2;

    private static final Color BEAM_COLOR = Color.YELLOW;
    private static final double BEAM_HEIGHT = 6.0;
    private static final int BEAM_POINTS_PER_BLOCK = 4;

    private KCZoneUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Spawn the platform + marker + timer entities for a zone and attach them to it, in the dim
     * "pending activation" look. Call {@link #activateZoneEntities} once the zone's activation
     * delay elapses to light them up.
     */
    public static void spawnZoneEntities(KCZone zone) {
        Location center = zone.getCenter();
        if (center == null || center.getWorld() == null) return;

        // Zone centers are victim death locations, so their yaw/pitch is whatever direction the
        // victim happened to be looking when they died. BlockDisplay entities inherit their own
        // spawn rotation into rendering (see FlagBannerUtils#createCarryingTask, which relies on
        // exactly this to orient the CTF banner) - without flattening it here, the platform would
        // spawn tilted whenever a player died looking up/down instead of lying flat on the ground.
        Location flatCenter = center.clone();
        flatCenter.setYaw(0f);
        flatCenter.setPitch(0f);

        Material platformMaterial = platformMaterialFor(zone.getKind());

        BlockDisplay platform = flatCenter.getWorld().spawn(flatCenter, BlockDisplay.class, display -> {
            display.setBlock(platformMaterial.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(-1.5f, -0.05f, -1.5f),
                    new AxisAngle4f(0, 0, 0, 1),
                    PLATFORM_SCALE,
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            display.setGlowing(false);
            display.setBrightness(PENDING_BRIGHTNESS);
        });
        zone.setPlatformDisplay(platform);

        Location iconLoc = flatCenter.clone().add(0, 1.2, 0);
        long pendingSeconds = pendingSecondsRemaining(zone, System.currentTimeMillis());
        Entity icon = switch (zone.getKind()) {
            case NAMETAG -> spawnNametag(iconLoc, pendingSeconds);
            case MONEY -> spawnIconItem(iconLoc, Material.EMERALD);
            case HEART -> spawnIconItem(iconLoc, Material.TOTEM_OF_UNDYING);
        };
        zone.setIconDisplay(icon);

        Location timerLoc = flatCenter.clone().add(0, 1.7, 0);
        zone.setTimerDisplay(spawnTimerDisplay(timerLoc, pendingSeconds));

        SoundUtils.playAt(flatCenter, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
    }

    /**
     * Whole seconds remaining until a zone's activation delay elapses (always at least 1 while
     * still pending), used for the "Activating... Ns" text shown in place of the victim's name.
     */
    private static long pendingSecondsRemaining(KCZone zone, long now) {
        long remainingMs = Math.max(0, zone.getActivatesAtMs() - now);
        return Math.max(1, Math.round(remainingMs / 1000.0));
    }

    /**
     * Swap a zone's entities from the dim "pending" look to the lit, kind-colored look. Call
     * once, the first tick after {@link KCZone#isPendingActivation} goes false.
     */
    public static void activateZoneEntities(KCZone zone) {
        Color glowColor = glowColorFor(zone.getKind());
        String colorTag = colorTagFor(zone.getKind());

        BlockDisplay platform = zone.getPlatformDisplay();
        if (platform != null && !platform.isDead()) {
            platform.setGlowing(true);
            platform.setGlowColorOverride(glowColor);
            platform.setBrightness(ACTIVE_BRIGHTNESS);
        }

        // Glowing renders through walls independent of the model's own depth test - the only
        // way to make an ItemDisplay (money/heart icon) visible through walls at all, and it
        // reinforces the TextDisplay nametag's setSeeThrough(true) with the same colored outline
        // the platform gets.
        Entity icon = zone.getIconDisplay();
        if (icon instanceof TextDisplay nametag && !nametag.isDead()) {
            nametag.text(Messages.parse("<" + colorTag + "><bold>" + zone.getVictimName() + "'s Tag</bold></" + colorTag + ">"));
            nametag.setBrightness(ACTIVE_BRIGHTNESS);
            nametag.setGlowing(true);
            nametag.setGlowColorOverride(glowColor);
        } else if (icon instanceof Display displayIcon && !icon.isDead()) {
            displayIcon.setBrightness(ACTIVE_BRIGHTNESS);
            displayIcon.setGlowing(true);
            displayIcon.setGlowColorOverride(glowColor);
        }

        TextDisplay timer = zone.getTimerDisplay();
        if (timer != null && !timer.isDead()) {
            timer.setBrightness(ACTIVE_BRIGHTNESS);
        }
    }

    /**
     * Refresh a zone's countdown timer text to the whole seconds remaining until expiry.
     * No-ops for zones without a timer, or when the displayed number hasn't changed since the
     * last call.
     */
    public static void updateTimerDisplay(KCZone zone, long now) {
        TextDisplay timer = zone.getTimerDisplay();
        if (timer == null || timer.isDead()) return;

        long remainingMs = Math.max(0, zone.getExpiresAtMs() - now);
        int secondsRemaining = (int) Math.ceil(remainingMs / 1000.0);
        if (secondsRemaining == zone.getLastDisplayedTimerSeconds()) return;
        zone.setLastDisplayedTimerSeconds(secondsRemaining);

        String colorTag = colorTagFor(zone.getKind());
        timer.text(Messages.parse("<" + colorTag + "><bold>" + secondsRemaining + "s</bold></" + colorTag + ">"));
    }

    /**
     * Swap a zone's timer text to "Contested!" once it's past its normal expiry but a
     * killer-team member is still standing in it. No-ops for zones without a timer, or if
     * "Contested!" is already showing.
     */
    public static void showContested(KCZone zone) {
        TextDisplay timer = zone.getTimerDisplay();
        if (timer == null || timer.isDead()) return;
        if (zone.getLastDisplayedTimerSeconds() == CONTESTED_SENTINEL) return;
        zone.setLastDisplayedTimerSeconds(CONTESTED_SENTINEL);

        timer.text(Messages.parse("<red><bold>Contested!</bold></red>"));
    }

    private static Material platformMaterialFor(KCZone.ZoneKind kind) {
        return switch (kind) {
            case NAMETAG -> Material.GOLD_BLOCK;
            case MONEY -> Material.EMERALD_BLOCK;
            case HEART -> Material.REDSTONE_BLOCK;
        };
    }

    private static Color glowColorFor(KCZone.ZoneKind kind) {
        return switch (kind) {
            case NAMETAG -> GOLD_GLOW;
            case MONEY -> GREEN_GLOW;
            case HEART -> RED_GLOW;
        };
    }

    private static String colorTagFor(KCZone.ZoneKind kind) {
        return switch (kind) {
            case NAMETAG -> "gold";
            case MONEY -> "green";
            case HEART -> "red";
        };
    }

    private static TextDisplay spawnNametag(Location iconLoc, long pendingSeconds) {
        return iconLoc.getWorld().spawn(iconLoc, TextDisplay.class, display -> {
            display.text(Messages.parse(pendingActivationText(pendingSeconds)));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(PENDING_BRIGHTNESS);
            display.setSeeThrough(true);
            display.setShadowed(false);
        });
    }

    private static String pendingActivationText(long pendingSeconds) {
        return "<gray><bold>Activating... " + pendingSeconds + "s</bold></gray>";
    }

    /**
     * Refresh a zone's "Activating... Ns" text while it's still in its activation delay - on
     * the NAMETAG icon (the only icon that's text-capable; MONEY/HEART use an item icon) and on
     * the timer display, which every zone kind has.
     */
    public static void updatePendingActivationDisplay(KCZone zone, long now) {
        String text = pendingActivationText(pendingSecondsRemaining(zone, now));

        if (zone.getIconDisplay() instanceof TextDisplay nametag && !nametag.isDead()) {
            nametag.text(Messages.parse(text));
        }

        TextDisplay timer = zone.getTimerDisplay();
        if (timer != null && !timer.isDead()) {
            timer.text(Messages.parse(text));
        }
    }

    /**
     * A brief yellow beam pulsing straight up from a zone - shown once when it activates and
     * again when its countdown timer reaches the halfway point.
     */
    public static void spawnActivationBeam(Location center) {
        if (center == null || center.getWorld() == null) return;
        ParticleUtils.verticalBeam(center.clone().add(0, 0.2, 0), BEAM_COLOR, BEAM_HEIGHT, BEAM_POINTS_PER_BLOCK);
    }

    private static TextDisplay spawnTimerDisplay(Location loc, long pendingSeconds) {
        return loc.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.text(Messages.parse(pendingActivationText(pendingSeconds)));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(PENDING_BRIGHTNESS);
            display.setSeeThrough(true);
            display.setShadowed(false);
        });
    }

    private static ItemDisplay spawnIconItem(Location iconLoc, Material material) {
        return iconLoc.getWorld().spawn(iconLoc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(material));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(PENDING_BRIGHTNESS);
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
     * Remove a zone's platform, marker, and timer entities.
     */
    public static void despawnZoneEntities(KCZone zone) {
        if (zone == null) return;
        if (zone.getPlatformDisplay() != null && !zone.getPlatformDisplay().isDead()) {
            zone.getPlatformDisplay().remove();
        }
        if (zone.getIconDisplay() != null && !zone.getIconDisplay().isDead()) {
            zone.getIconDisplay().remove();
        }
        if (zone.getTimerDisplay() != null && !zone.getTimerDisplay().isDead()) {
            zone.getTimerDisplay().remove();
        }

        SoundUtils.playAt(zone.getCenter(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 1.0f);
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
