package me.psikuvit.cashClash.util.effects;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/**
 * Utility helpers that simplify spawning particles both globally and per-player,
 * with safe null-checking and convenience overloads.
 */
public final class ParticleUtils {

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

    public static void circle(Particle particle, Location center, double radius, double height, int points, double extra) {
        if (particle == null || center == null || center.getWorld() == null) return;
        points = Math.max(4, points);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location spawn = new Location(center.getWorld(), x, center.getY() + height, z);
            spawn(particle, spawn, 1, 0, 0, 0, extra);
        }
    }

    public static void helix(Particle particle, Location center, double radius, double height, int turns, int pointsPerTurn, double extra) {
        if (particle == null || center == null || center.getWorld() == null) return;
        int total = Math.max(1, turns) * Math.max(4, pointsPerTurn);
        for (int i = 0; i < total; i++) {
            double t = (double) i / total;
            double angle = t * turns * 2 * Math.PI;
            double y = center.getY() + height * t;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location spawn = new Location(center.getWorld(), x, y, z);
            center.getWorld().spawnParticle(particle, spawn, 1, 0, 0, 0, extra);
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
            base.getWorld().spawnParticle(Particle.DUST, point, particlesPerPoint, 0.05, 0, 0.05, dustOptions);
        }
    }

    public static void vectorBurst(Particle particle, Location center, Vector direction, double spread, int count, double extra) {
        if (particle == null || center == null || center.getWorld() == null || direction == null) return;
        for (int i = 0; i < Math.max(1, count); i++) {
            Vector v = direction.clone().rotateAroundY((Math.random() - 0.5) * spread).normalize();
            center.getWorld().spawnParticle(particle, center, 0, v.getX(), v.getY(), v.getZ(), extra);
        }
    }

    // ==================== MYTHIC ITEM EFFECTS ====================

    /**
     * Spawn blood sphere particles (for BloodWrench Rapid Fire).
     */
    public static void bloodSphere(Location location, double radius, int count) {
        spawnDust(location, Color.fromRGB(139, 0, 0), 2.0f, count, radius);
    }

    /**
     * Spawn lingering blood sphere particles.
     */
    public static void bloodSphereLingering(Location location, double radius) {
        spawnDust(location, Color.fromRGB(139, 0, 0), 1.5f, 20, radius * 0.8);
    }

    /**
     * Spawn spiraling blood vortex particles (for BloodWrench Supercharged).
     * @param location Center location
     * @param radius Vortex radius
     * @param tick Current animation tick
     */
    public static void bloodVortexSpiral(Location location, double radius, int tick) {
        if (location == null || location.getWorld() == null) return;

        double angle = tick * 0.3;
        for (int i = 0; i < 3; i++) {
            double offsetAngle = angle + (i * (Math.PI * 2 / 3));
            double x = Math.cos(offsetAngle) * radius * 0.8;
            double z = Math.sin(offsetAngle) * radius * 0.8;
            double y = (tick % 20) * 0.15; // Spiral up

            spawnDust(location.clone().add(x, y, z), Color.fromRGB(180, 0, 0), 2.0f, 5, 0.1);
        }

        // Central column of particles
        spawnDust(location.clone().add(0, 1.5, 0), Color.fromRGB(100, 0, 0), 1.5f, 15, 0.3, 1.5, 0.3);
    }

    /**
     * Spawn blood vortex explosion effect at the end.
     */
    public static void bloodVortexExplosion(Location location, double radius) {
        spawnDust(location.clone().add(0, 1, 0), Color.fromRGB(139, 0, 0), 3.0f, 80, radius, 2, radius);
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
     * Spawn volcano explosion effect (for BlazeBite Volcano).
     */
    public static void volcanoExplosion(Location location) {
        flame(location, 50, 1);
        explosion(location);
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
     * Spawn a circle of dust particles (Dragon Rush departure/arrival).
     */
    public static void dragonRushCircle(Location center, Color color, float size) {
        if (center == null || center.getWorld() == null) return;
        Particle.DustOptions options = new Particle.DustOptions(color, size);
        for (int i = 0; i < 24; i++) {
            double angle = 2 * Math.PI * i / 24;
            double x = Math.cos(angle) * 1.2;
            double z = Math.sin(angle) * 1.2;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, 0.2, z), 1, options);
        }
    }

    /**
     * Spawn a single Dragon Fury veil particle (swirl built up over time by the caller).
     */
    public static void dragonFuryVeil(Location location, Color color) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.DUST, location, 1, new Particle.DustOptions(color, 1.4f));
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
     */
    public static void guardianRings(Location playerLocation) {
        if (playerLocation == null || playerLocation.getWorld() == null) return;
        Color turquoise = Color.fromRGB(40, 220, 180);
        Color orange = Color.fromRGB(255, 140, 40);
        for (int i = 0; i < 3; i++) {
            double radius = 0.8 + (i * 0.25);
            for (int j = 0; j < 28; j++) {
                double angle = 2 * Math.PI * j / 28;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Color color = (j % 7 == 0) ? orange : turquoise;
                spawnDust(playerLocation.clone().add(x, 1.8, z), color, 1.8f, 1);
            }
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
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = center.clone().add(x, 1.0 + (Math.random() * 0.4 - 0.2), z);
            Color color = (angle % 0.3 < 0.15) ? black : red;
            spawnDust(particleLoc, color, 1.2f, 1);
        }
    }

    /**
     * Spawn one frame of the purple spiral flight trail for Dragon Outrage.
     */
    public static void dragonOutrageTrail(Location location) {
        if (location == null || location.getWorld() == null) return;
        spawnDust(location, Color.fromRGB(220, 170, 255), 1.2f, 3, 0.2);
        spawnDust(location, Color.fromRGB(140, 0, 255), 1.2f, 3, 0.2);
        spawn(Particle.PORTAL, location, 4, 0.3, 0.3, 0.3, 0.05);
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
        double x = center.getX() + currentRadius * Math.cos(angle);
        double z = center.getZ() + currentRadius * Math.sin(angle);
        Location point = new Location(center.getWorld(), x, center.getY() + 0.2, z);

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
        int clampedFormed = Math.min(totalPoints, Math.max(0, formedCount));
        for (int i = 0; i < clampedFormed; i++) {
            double angle = 2 * Math.PI * i / totalPoints;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            spawnDust(new Location(center.getWorld(), x, center.getY(), z), color, size, 1, 0);
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
        int clampedFormed = Math.min(totalPoints, Math.max(0, formedCount));
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
        spawnDust(location, Color.fromRGB(173, 216, 230), 1.2f, 12, 0.5, 0.4, 0.5);
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
