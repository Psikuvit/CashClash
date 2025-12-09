package me.psikuvit.cashClash.util;

import org.bukkit.Bukkit;
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

    public static void spawn(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (particle == null || location == null) return;
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
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
}

