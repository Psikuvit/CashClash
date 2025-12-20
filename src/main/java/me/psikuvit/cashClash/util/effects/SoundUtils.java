package me.psikuvit.cashClash.util.effects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Small helper utilities for playing sounds safely and conveniently.
 */
public final class SoundUtils {

    private SoundUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Play a sound to a single player at their current location.
     */
    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        if (!player.isOnline()) return;
        player.playSound(player.getLocation(), sound, volume, pitch);

    }

    /**
     * Play a sound at an arbitrary location (world must be non-null).
     */
    public static void playAt(Location location, Sound sound, float volume, float pitch) {
        if (location == null || sound == null) return;
        if (location.getWorld() == null) return;
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    /**
     * Play a sound to a collection of player UUIDs (skips offline players).
     */
    public static void playTo(Collection<UUID> players, Sound sound, float volume, float pitch) {
        if (players == null || sound == null) return;
        for (UUID u : players) {
            if (u == null) continue;
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * Play a sound to every online player on the server.
     */
    public static void playToAll(Sound sound, float volume, float pitch) {
        if (sound == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}

