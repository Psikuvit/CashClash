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
            center.getWorld().spawnParticle(particle, spawn, 1, 0, 0, 0, extra);
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
}
