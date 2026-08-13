package me.psikuvit.cashClash.util.effects;

import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Utility helpers that simplify spawning particles both globally and per-player,
 * with safe null-checking and convenience overloads.
 */
public final class ParticleUtils {

    private static final int EMERALD_RING_COUNT = 6;
    private static final double EMERALD_RING_RADIUS = 0.9;
    private static final int EMERALD_RING_DURATION_TICKS = 30;

    private ParticleUtils() {
        throw new AssertionError("Nope");
    }

    // ==================== BASIC SPAWN METHODS ====================

    /**
     * Spawn a particle at a location with full control over parameters.
     */
    public static void spawn(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (particle == null || location == null) return;
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    /**
     * Spawn a particle at a location with simple offset.
     */
    public static void spawn(Particle particle, Location location, int count, double offset) {
        spawn(particle, location, count, offset, offset, offset, 0);
    }

    /**
     * Spawn a particle with simple offset and particle data (e.g. BlockData for
     * particles that require it, like {@link Particle#FALLING_DUST}).
     */
    public static void spawn(Particle particle, Location location, int count, double offset, Object data) {
        spawn(particle, location, count, offset, offset, offset, 0, data);
    }

    /**
     * Spawn a particle with full control over parameters and particle data.
     */
    public static void spawn(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        if (particle == null || location == null) return;
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    /**
     * Spawn a particle at a location with no offset.
     */
    public static void spawn(Particle particle, Location location, int count) {
        spawn(particle, location, count, 0, 0, 0, 0);
    }

    /**
     * Spawn a colored dust particle.
     */
    public static void spawnDust(Location location, Color color, float size, int count, double offsetX, double offsetY, double offsetZ) {
        if (location == null || location.getWorld() == null || color == null) return;
        location.getWorld().spawnParticle(Particle.DUST, location, count, offsetX, offsetY, offsetZ,
                new Particle.DustOptions(color, size));
    }

    /**
     * Spawn a colored dust particle with simple offset.
     */
    public static void spawnDust(Location location, Color color, float size, int count, double offset) {
        spawnDust(location, color, size, count, offset, offset, offset);
    }

    /**
     * Spawn a colored dust particle with no offset.
     */
    public static void spawnDust(Location location, Color color, float size, int count) {
        spawnDust(location, color, size, count, 0, 0, 0);
    }

    public static void spawnForPlayer(Player player, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (player == null || !player.isOnline() || particle == null || location == null) return;
        player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawnFor(Collection<UUID> players, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (players == null || particle == null || location == null) return;
        for (UUID u : players) {
            if (u == null) continue;
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            p.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    // ==================== CONVENIENCE METHODS ====================

    /**
     * Spawn an explosion particle.
     */
    public static void explosion(Location location) {
        spawn(Particle.EXPLOSION, location, 1);
    }

    /**
     * Spawn heart particles above a player.
     */
    public static void hearts(Player player, int count) {
        if (player == null) return;
        spawn(Particle.HEART, player.getLocation().add(0, 2, 0), count);
    }

    /**
     * Spawn cloud particles at a location.
     */
    public static void cloud(Location location, int count, double offset) {
        spawn(Particle.CLOUD, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn portal particles at a location.
     */
    public static void portal(Location location, int count, double offset) {
        spawn(Particle.PORTAL, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn totem particles at a location.
     */
    public static void totem(Location location, int count, double offset) {
        spawn(Particle.TOTEM_OF_UNDYING, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn critical hit particles at a location.
     */
    public static void crit(Location location, int count, double offset) {
        spawn(Particle.CRIT, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn sweep attack particles at a location.
     */
    public static void sweep(Location location) {
        spawn(Particle.SWEEP_ATTACK, location, 1);
    }

    /**
     * Spawn electric spark particles at a location.
     */
    public static void electricSpark(Location location, int count, double offset) {
        spawn(Particle.ELECTRIC_SPARK, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn sonic boom particle at a location.
     */
    public static void sonicBoom(Location location) {
        spawn(Particle.SONIC_BOOM, location, 1);
    }

    /**
     * Spawn damage indicator particles at a location.
     */
    public static void damageIndicator(Location location, int count, double offset) {
        spawn(Particle.DAMAGE_INDICATOR, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn slime particles at a location.
     */
    public static void slime(Location location, int count, double offset) {
        spawn(Particle.ITEM_SLIME, location, count, offset, offset, offset, 0.1);
    }

    /**
     * Spawn campfire smoke particles at a location.
     */
    public static void campfireSmoke(Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawn(Particle.CAMPFIRE_SIGNAL_SMOKE, location, count, offsetX, offsetY, offsetZ, 0.01);
    }

    /**
     * Spawn snowflake particles at a location.
     */
    public static void snowflake(Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawn(Particle.SNOWFLAKE, location, count, offsetX, offsetY, offsetZ, extra);
    }

    /**
     * Spawn flame particles at a location.
     */
    public static void flame(Location location, int count, double offset) {
        spawn(Particle.FLAME, location, count, offset, offset, offset, 0.2);
    }

    // ==================== SHAPE METHODS ====================

    /**
     * The angle of point {@code i} of {@code totalPoints} evenly spaced around a full circle -
     * shared by every ring/circle shape below instead of each recomputing it.
     */
    private static double ringAngle(int i, int totalPoints) {
        return 2 * Math.PI * i / totalPoints;
    }

    /**
     * A point on a circle of {@code radius} around {@code center}'s X/Z, raised {@code yOffset}
     * above {@code center}'s Y - the shared building block for every ring/circle/spiral shape
     * below instead of each recomputing the same cos/sin.
     */
    private static Location circlePoint(Location center, double radius, double angle, double yOffset) {
        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        return new Location(center.getWorld(), x, center.getY() + yOffset, z);
    }

    public static void circle(Particle particle, Location center, double radius, double height, int points, double extra) {
        if (particle == null || center == null || center.getWorld() == null) return;
        points = Math.max(4, points);
        for (int i = 0; i < points; i++) {
            spawn(particle, circlePoint(center, radius, ringAngle(i, points), height), 1, 0, 0, 0, extra);
        }
    }

    public static void circle(Location center, double radius, int points, double height, Color color, float size) {
        if (center == null || center.getWorld() == null) return;
        points = Math.max(4, points);
        for (int i = 0; i < points; i++) {
            spawnDust(circlePoint(center, radius, ringAngle(i, points), height), color, size, 1);
        }
    }

    public static void helix(Particle particle, Location center, double radius, double height, int turns, int pointsPerTurn, double extra) {
        if (particle == null || center == null || center.getWorld() == null) return;
        int total = Math.max(1, turns) * Math.max(4, pointsPerTurn);
        for (int i = 0; i < total; i++) {
            double t = (double) i / total;
            double angle = t * turns * 2 * Math.PI;
            spawn(particle, circlePoint(center, radius, angle, height * t), 1, 0, 0, 0, extra);
        }
    }

    /**
     * Spawn a one-shot vertical column of colored dust particles straight up from a base
     * location - used for the KC zone activation/halfway beam pulse.
     */
    public static void verticalBeam(Location base, Color color, double height, int pointsPerBlock, float size, int particlesPerPoint) {
        if (base == null || base.getWorld() == null || color == null) return;
        int totalPoints = Math.max(1, (int) Math.round(height * pointsPerBlock));
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, size);
        for (int i = 0; i <= totalPoints; i++) {
            double y = base.getY() + (height * i / totalPoints);
            Location point = new Location(base.getWorld(), base.getX(), y, base.getZ());
            spawn(Particle.DUST, point, particlesPerPoint, 0.05, 0, 0.05, 0, dustOptions);
        }
    }

    public static void vectorBurst(Particle particle, Location center, Vector direction, double spread, int count, double extra) {
        if (particle == null || center == null || center.getWorld() == null || direction == null) return;
        for (int i = 0; i < Math.max(1, count); i++) {
            Vector v = direction.clone().rotateAroundY((Math.random() - 0.5) * spread).normalize();
            spawn(particle, center, 0, v.getX(), v.getY(), v.getZ(), extra);
        }
    }

    /**
     * Fires 5 short colored-dust lines radiating out from a point, all fanned within the single
     * vertical plane flush against the wall (perpendicular to {@code forward}) rather than
     * poking into it: two straight to the sides (+/-90 deg), two diagonal (+/-45 deg, blended
     * between a side and straight up - not rotated toward {@code forward} the way the sides are,
     * which used to angle them into the wall and make them look like they "flared" instead of
     * evenly filling the gap between a side line and the up line), and one straight up. Used for
     * Goblin Spear's wall-impact burst. Reuses {@link #beam} for each line's point-stepping.
     */
    public static void radialLineBurst(Location origin, Vector forward, Color color, double lineLength, int pointsPerBlock) {
        if (origin == null || origin.getWorld() == null || forward == null) return;

        Vector flatForward = forward.clone().setY(0);
        if (flatForward.lengthSquared() < 1.0E-4) flatForward = new Vector(1, 0, 0);
        flatForward.normalize();

        Vector right = flatForward.clone().rotateAroundY(Math.toRadians(90));
        Vector up = new Vector(0, 1, 0);

        // Fan from -90 deg (left) through 0 (straight up) to +90 deg (right), all in the
        // {right, up} plane - none of these have a forward component, so nothing pokes into
        // the wall the burst just hit.
        Vector[] directions = new Vector[] {
                right.clone().multiply(-1),                                              // left
                right.clone().multiply(-Math.cos(Math.toRadians(45))).add(up.clone().multiply(Math.sin(Math.toRadians(45)))), // up-left diagonal
                up,                                                                       // straight up
                right.clone().multiply(Math.cos(Math.toRadians(45))).add(up.clone().multiply(Math.sin(Math.toRadians(45)))),  // up-right diagonal
                right                                                                     // right
        };

        for (Vector direction : directions) {
            Location end = origin.clone().add(direction.clone().normalize().multiply(lineLength));
            beam(origin, end, color, 1.2f, pointsPerBlock);
        }
    }

    // ==================== MYTHIC ITEM EFFECTS ====================

    /**
     * Spawn blood sphere particles (for BloodWrench Rapid Fire).
     */
    public static void bloodSphere(Location location, double radius, int count) {
        spawnDust(location, Color.fromRGB(139, 0, 0), 2.0f, count, radius);
    }

    private static final int BLOOD_SPHERE_RINGS = 9;

    /**
     * Draws the BloodWrench Rapid Fire blood sphere as a hollow shell: a stack of latitude rings
     * whose radii follow sin(phi), spun a little per tick so it reads as alive rather than as a
     * static bubble. Only the surface is drawn - the interior stays clear so players caught
     * inside remain visible.
     *
     * @param location centre of the sphere
     * @param radius   shell radius, matching the sphere's effect radius
     * @param tick     current animation tick, drives the spin
     * @param density  points per block of ring radius - higher packs the shell tighter
     */
    public static void bloodSphereShell(Location location, double radius, int tick, double density) {
        if (location == null || location.getWorld() == null) return;

        Color shell = Color.fromRGB(180, 0, 0);
        double spin = tick * 0.08;

        for (int ring = 1; ring <= BLOOD_SPHERE_RINGS; ring++) {
            double phi = Math.PI * ring / (BLOOD_SPHERE_RINGS + 1);
            double ringRadius = radius * Math.sin(phi);
            double y = radius * Math.cos(phi);

            // Keep spacing even across the shell: wider rings need proportionally more points.
            int points = Math.max(6, (int) Math.round(ringRadius * density));
            for (int i = 0; i < points; i++) {
                double angle = spin + (Math.PI * 2 * i / points);
                spawnDust(circlePoint(location, ringRadius, angle, y), shell, 1.4f, 1);
            }
        }

        spawnDust(location.clone().add(0, radius, 0), shell, 1.4f, 1);
        spawnDust(location.clone().subtract(0, radius, 0), shell, 1.4f, 1);
    }

    /**
     * Draws the BloodWrench Supercharged vortex as a funnel of twisting strands - narrow at the
     * base, widening toward the top. Each strand is walked as a continuous helix rather than a
     * single orbiting point, so the tornado reads as solid instead of as a few drifting dots.
     *
     * @param location centre of the funnel's base
     * @param radius   funnel radius at its widest (the top)
     * @param tick     current animation tick, drives the spin
     * @param strands  number of strands winding around the funnel
     * @param density  points drawn per strand, per call
     */
    public static void bloodVortexSpiral(Location location, double radius, int tick, int strands, int density) {
        if (location == null || location.getWorld() == null) return;

        Color strandColor = Color.fromRGB(180, 0, 0);
        double baseAngle = tick * 0.3;

        for (int strand = 0; strand < strands; strand++) {
            double strandOffset = strand * (Math.PI * 2 / strands);
            for (int i = 0; i < density; i++) {
                double progress = (double) i / density;
                // Climb while twisting, so consecutive points trace a helix up the funnel.
                double angle = baseAngle + strandOffset + progress * Math.PI * 2;
                double ringRadius = radius * (0.25 + 0.75 * progress);
                spawnDust(circlePoint(location, ringRadius, angle, progress * radius), strandColor, 1.6f, 1);
            }
        }

        spawnDust(location.clone().add(0, radius * 0.5, 0), Color.fromRGB(100, 0, 0), 1.5f, density, 0.3, radius * 0.5, 0.3);
    }

    /**
     * Pulse of red particles around a player, fired each time they heal from the BloodWrench
     * vortex - a ring at their feet plus a burst around chest height reads as one "pulse".
     */
    public static void bloodHealPulse(Player player) {
        if (player == null) return;
        Location base = player.getLocation();
        Color color = Color.fromRGB(200, 0, 0);

        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            double x = Math.cos(angle) * 0.6;
            double z = Math.sin(angle) * 0.6;
            spawnDust(base.clone().add(x, 1.0, z), color, 1.5f, 1, 0);
        }
        spawn(Particle.HEART, base.clone().add(0, 2.2, 0), 1);
    }

    /**
     * Spawn glacier frost particles (for BlazeBite Glacier).
     */
    public static void glacierFrost(Location location) {
        snowflake(location.add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
    }

    /**
     * Spawn freeze-in-place particles above player head.
     */
    public static void freezeParticles(Location headLocation) {
        snowflake(headLocation.add(0, 2.2, 0), 15, 0.3, 0.2, 0.3, 0.05);
    }

    /**
     * Spawn frostbite particles (lighter blue, during initial freeze).
     */
    public static void frostbiteParticles(Location headLocation) {
        spawnDust(headLocation.add(0, 2.2, 0), Color.fromRGB(135, 206, 250), 1.0f, 10, 0.3, 0.2, 0.3);
    }

    /**
     * Draws a small blue heart outline above a location - the "you're freezing" damage
     * indicator used by Ice Fan's stacking freeze (mirrors the codebase's other custom
     * parametric shapes, e.g. deathmaulerPermanentHeart, since vanilla's Particle.HEART can't
     * be recolored). Drawn on two perpendicular planes (XY and ZY) so it reads as a heart from
     * any horizontal viewing angle instead of only face-on.
     */
    public static void blueFreezeHeart(Location location) {
        if (location == null || location.getWorld() == null) return;
        Color blue = Color.fromRGB(80, 170, 255);
        double scale = 0.045;
        for (int i = 0; i < 20; i++) {
            double t = (2 * Math.PI * i) / 20;
            double hx = 16 * Math.pow(Math.sin(t), 3);
            double hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            spawnDust(location.clone().add(hx * scale, hy * scale, 0), blue, 1.3f, 1, 0);
            spawnDust(location.clone().add(0, hy * scale, hx * scale), blue, 1.3f, 1, 0);
        }
    }

    /**
     * Spawn volcano explosion effect (for BlazeBite Volcano).
     */
    public static void volcanoExplosion(Location location) {
        flame(location, 70, 1.5);
        explosion(location);
    }

    /**
     * Spawn a red/orange/yellow dust burst left behind at a Volcano-mode impact point
     * (BlazeBite). Smaller/tighter than {@link #flamebringerPull} since this is a one-shot
     * burst, not a lingering pull effect. Spread/size bumped up so the burst reads clearly
     * from a distance.
     */
    public static void volcanoFlameBurst(Location location) {
        spawnDust(location, Color.fromRGB(255, 0, 0), 1.6f, 18, 1.0);
        spawnDust(location, Color.fromRGB(255, 165, 0), 1.6f, 18, 1.0);
        spawnDust(location, Color.fromRGB(255, 255, 0), 1.6f, 18, 1.0);
    }

    /**
     * Spawn hit feedback particles (crit at target location).
     */
    public static void hitFeedback(Location targetLocation, int count, double offset) {
        crit(targetLocation.add(0, 1, 0), count, offset);
    }

    /**
     * Spawn spin attack sweep particles at location.
     */
    public static void spinSweep(Location attackerLocation, double angle, double radius) {
        double px = Math.cos(angle + Math.PI) * radius;
        double pz = Math.sin(angle + Math.PI) * radius;
        sweep(attackerLocation.add(px, 1, pz));
    }

    // ==================== CUSTOM ARMOR EFFECTS ====================

    /**
     * Spawn dragon mark particles above marked player.
     */
    public static void bullseyeStorm(Location location) {
        // Red and White dust particles outward
        spawnDust(location, Color.RED, 1.2f, 15, 0.3, 0.3, 0.3);
        spawnDust(location, Color.WHITE, 1.2f, 15, 0.3, 0.3, 0.3);
        
        // Critical hit particles for impact feel
        crit(location, 10, 0.5);
    }

    public static void dragonMark(Location location) {
        spawnDust(location.clone().add(0, 2.5, 0), Color.fromRGB(138, 43, 226), 2.0f, 15, 0.3, 0.2, 0.3);
    }

    /**
     * Spawn a circle of dust particles (Dragon Rush departure/arrival) - a fully-formed
     * {@link #formingRing}, 0.2 blocks off the ground.
     */
    public static void dragonRushCircle(Location center, Color color, float size) {
        if (center == null || center.getWorld() == null) return;
        formingRing(center.clone().add(0, 0.2, 0), 1.2, 24, 24, color, size);
    }

    /**
     * Spawn a single Dragon Fury veil particle (swirl built up over time by the caller).
     */
    public static void dragonFuryVeil(Location location, Color color) {
        spawnDust(location, color, 1.4f, 1);
        spawn(Particle.PORTAL, location, 1, 0, 0, 0, 0.01);
    }

    /**
     * Spawn a dragon dash trail particles.
     */
    public static void dragonDashTrail(Location location) {
        spawnDust(location, Color.fromRGB(138, 43, 226), 1.5f, 20, 0.5);
        spawn(Particle.DRAGON_BREATH, location, 5, 0.5);
    }

    /**
     * Spawn the white/blue diamond burst for Bunny Shoes activation.
     */
    public static void bunnyDiamond(Location center) {
        if (center == null || center.getWorld() == null) return;
        Color white = Color.WHITE;
        Color blue = Color.fromRGB(120, 200, 255);
        double[][] diamond = {
                { 0.0,  0.85},
                { 0.30, 0.60},
                { 0.60, 0.30},
                { 0.85, 0.0},
                { 0.60,-0.30},
                { 0.30,-0.60},
                { 0.0, -0.85},
                {-0.30,-0.60},
                {-0.60,-0.30},
                {-0.85, 0.0},
                {-0.60, 0.30},
                {-0.30, 0.60}
        };
        for (double[] point : diamond) {
            Location particleLoc = center.clone().add(point[0], 0, point[1]);
            spawnDust(particleLoc, white, 1.2f, 2, 0.03);
            spawnDust(particleLoc.clone().add(0, 0.08, 0), blue, 1.2f, 2, 0.03);
        }
    }

    /**
     * Spawn the turquoise/orange shield rings for Guardian's Vest activation.
     *
     * @param height how far above the wearer's feet to draw the rings
     */
    public static void guardianRings(Location playerLocation, double height) {
        if (playerLocation == null || playerLocation.getWorld() == null) return;
        Color turquoise = Color.fromRGB(40, 220, 180);
        Color orange = Color.fromRGB(255, 140, 40);
        for (int i = 0; i < 3; i++) {
            double radius = 0.8 + (i * 0.25);
            for (int j = 0; j < 28; j++) {
                Color color = (j % 7 == 0) ? orange : turquoise;
                spawnDust(circlePoint(playerLocation, radius, ringAngle(j, 28), height), color, 1.8f, 1);
            }
        }
    }

    /**
     * A ring of six solid emeralds spinning briefly around the player - the shared coin
     * reward flourish for Investor's Set and Cash Blaster payouts. Schedules its own frames.
     */
    public static void emeraldRing(Player player) {
        if (player == null || !player.isOnline()) return;

        World world = player.getWorld();

        ItemStack emerald = new ItemStack(Material.EMERALD);
        List<ItemDisplay> ring = new ArrayList<>();
        for (int i = 0; i < EMERALD_RING_COUNT; i++) {
            ring.add(world.spawn(player.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(emerald);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setInterpolationDuration(1);
                d.setTeleportDuration(1);
            }));
        }

        for (int tick = 0; tick <= EMERALD_RING_DURATION_TICKS; tick++) {
            final int step = tick;
            SchedulerUtils.runTaskLater(() -> {
                if (step == EMERALD_RING_DURATION_TICKS || !player.isOnline()) {
                    ring.forEach(d -> { if (!d.isDead()) d.remove(); });
                    return;
                }
                double progress = step / (double) EMERALD_RING_DURATION_TICKS;
                Location center = player.getLocation().clone().add(0, 1.0, 0);
                float spin = (float) Math.toDegrees(progress * Math.PI * 4);
                for (int i = 0; i < ring.size(); i++) {
                    ItemDisplay display = ring.get(i);
                    if (display.isDead()) continue;
                    double angle = ringAngle(i, ring.size()) + (progress * Math.PI * 4);
                    display.teleport(circlePoint(center, EMERALD_RING_RADIUS, angle, 0));
                    display.setRotation(spin, 0f);
                }
            }, step);
        }
    }

    /**
     * Spawn one expanding Soul Burst wave ring (black/red alternating dust).
     */
    public static void soulBurstRing(Location center, double radius) {
        if (center == null || center.getWorld() == null) return;
        Color red = Color.RED;
        Color black = Color.BLACK;
        for (double angle = 0; angle < Math.PI * 2; angle += 0.15) {
            double yJitter = 1.0 + (Math.random() * 0.4 - 0.2);
            Color color = (angle % 0.3 < 0.15) ? black : red;
            spawnDust(circlePoint(center, radius, angle, yJitter), color, 1.2f, 1);
        }
    }

    /**
     * Spawn fiery gravitational pull particles (Flamebringer).
     */
    public static void flamebringerPull(Location center, double radius) {
        // Red, orange, yellow particles in a spiral
        spawnDust(center, Color.fromRGB(255, 0, 0), 1.5f, 30, radius);
        spawnDust(center, Color.fromRGB(255, 165, 0), 1.5f, 30, radius);
        spawnDust(center, Color.fromRGB(255, 255, 0), 1.5f, 30, radius);
    }

    /**
     * Spawn permanent heart particle effect (figure 8 with skulls) for Deathmauler.
     */
    public static void deathmaulerPermanentHeart(Location location) {
        // Black particles in figure 8 pattern around player
        Location center = location.clone().add(0, 1, 0);
        for (int i = 0; i < 20; i++) {
            double t = (i / 20.0) * Math.PI * 2;
            double x = Math.sin(t) * 0.8;
            double y = Math.sin(2 * t) * 0.4;
            double z = Math.cos(t) * 0.8;
            Location particleLoc = center.clone().add(x, y, z);
            spawnDust(particleLoc, Color.BLACK, 1.5f, 2, 0.1);
        }
    }

    /**
     * Spawn small healing particles for normal Deathmauler kills.
     */
    public static void deathmaulerHeal(Location location) {
        spawnDust(location.clone().add(0, 1, 0), Color.BLACK, 1.0f, 20, 0.5);
        spawn(Particle.HEART, location.clone().add(0, 1, 0), 5, 0.5);
    }

    // ==================== CUSTOM ITEM EFFECTS ====================

    /**
     * Spawn one frame of an expanding black smoke spiral (grey/red hints) - used by Totem of
     * Haunting. Unlike {@link #helix}, the radius grows call-to-call rather than the height,
     * so callers drive the expansion by incrementing {@code currentRadius} once per tick.
     */
    public static void smokeSpiralFrame(Location center, double currentRadius, int armIndex, int totalArms) {
        if (center == null || center.getWorld() == null) return;
        double angle = currentRadius * 2.5 + armIndex * (2 * Math.PI / Math.max(1, totalArms));
        Location point = circlePoint(center, currentRadius, angle, 0.2);

        spawn(Particle.SMOKE, point, 2, 0.05, 0.05, 0.05, 0.01);
        if (Math.random() < 0.35) {
            spawnDust(point, Color.fromRGB(120, 20, 20), 1.2f, 1, 0.05); // red hint
        } else {
            spawnDust(point, Color.fromRGB(70, 70, 70), 1.2f, 1, 0.05); // grey hint
        }
    }

    /**
     * Draws only the first {@code formedCount} of {@code totalPoints} evenly-spaced points
     * around a circle - callers stagger {@code formedCount} from 1 up to {@code totalPoints}
     * via successive delayed calls so the ring visually forms over time rather than popping in
     * all at once (see KCZoneUtils.spawnActivationBeam for the same staggering idiom). Used by
     * Boombox's speed-buff ring.
     */
    public static void formingRing(Location center, double radius, int totalPoints, int formedCount, Color color, float size) {
        if (center == null || center.getWorld() == null) return;
        int clampedFormed = Math.clamp(formedCount, 0, totalPoints);
        for (int i = 0; i < clampedFormed; i++) {
            spawnDust(circlePoint(center, radius, ringAngle(i, totalPoints), 0), color, size, 1, 0);
        }
    }

    /**
     * Draws only the first {@code formedCount} points of a figure-8 (Lemniscate of Gerono) on
     * the ground. Two simultaneous calls with {@code reverse=false}/{@code true} walk the
     * parameter from opposite ends so the cursors converge - used by Blooming Rose's sakura
     * formation.
     */
    public static void figureEight(Location center, double size, Color color, int totalPoints, int formedCount, boolean reverse) {
        if (center == null || center.getWorld() == null) return;
        int clampedFormed = Math.clamp(formedCount, 0, totalPoints);
        for (int i = 0; i < clampedFormed; i++) {
            int step = reverse ? totalPoints - 1 - i : i;
            double t = 2 * Math.PI * step / totalPoints;
            double x = center.getX() + size * Math.sin(t);
            double z = center.getZ() + size * Math.sin(2 * t) / 2.0;
            spawnDust(new Location(center.getWorld(), x, center.getY(), z), color, 1.2f, 1, 0);
        }
    }

    /**
     * Colored dust along the line from {@code from} to {@code to}. Callers re-call each tick
     * with an updated {@code to} so the beam visually shrinks as a target is pulled closer -
     * used by Orb of Gravitation.
     */
    public static void beam(Location from, Location to, Color color, float size, int pointsPerBlock) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return;
        double distance = from.distance(to);
        if (distance < 0.1) return;
        int points = Math.max(1, (int) Math.ceil(distance * pointsPerBlock));
        for (int i = 0; i <= points; i++) {
            double f = i / (double) points;
            double x = from.getX() + (to.getX() - from.getX()) * f;
            double y = from.getY() + (to.getY() - from.getY()) * f;
            double z = from.getZ() + (to.getZ() - from.getZ()) * f;
            spawnDust(new Location(from.getWorld(), x, y, z), color, size, 1, 0);
        }
    }

    /**
     * Light-blue, chaotically-scattered gust particles - used by Ice Fan's continuous
     * left-click gust.
     */
    public static void iceFanGust(Location location) {
        spawnDust(location, Color.fromRGB(0, 255, 255), 1.2f, 8, 0.5, 0.4, 0.5);
        spawnDust(location, Color.fromRGB(255, 255, 255), 1.0f, 6, 0.5, 0.4, 0.5);
        spawn(Particle.SNOWFLAKE, location, 6, 0.4, 0.3, 0.4, 0.02);
    }

    /**
     * Dark-blue, tightly-condensed burst particles - used by Ice Fan's right-click burst.
     */
    public static void iceFanBurst(Location location) {
        spawnDust(location, Color.fromRGB(0, 0, 139), 1.6f, 25, 0.2, 0.15, 0.2);
    }

    /**
     * Draws a diamond/rhombus outline on the ground - used by Radiating Lotus to mark its
     * heal radius.
     */
    public static void groundDiamond(Location center, double radius, Color color) {
        if (center == null || center.getWorld() == null) return;
        Location[] corners = {
                center.clone().add(radius, 0, 0),
                center.clone().add(0, 0, radius),
                center.clone().add(-radius, 0, 0),
                center.clone().add(0, 0, -radius)
        };
        int pointsPerEdge = Math.max(2, (int) (radius * 4));
        for (int edge = 0; edge < corners.length; edge++) {
            Location from = corners[edge];
            Location to = corners[(edge + 1) % corners.length];
            Vector edgeVector = to.toVector().subtract(from.toVector());
            for (int i = 0; i <= pointsPerEdge; i++) {
                Location point = from.clone().add(edgeVector.clone().multiply((double) i / pointsPerEdge));
                spawnDust(point, color, 1.3f, 1, 0);
            }
        }
    }
}
