package me.psikuvit.cashClash.game.round;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks data for the current round.
 * Uses mutable stats objects for better performance during frequent updates.
 */
public class RoundData {

    private final Map<UUID, PlayerRoundStats> stats;
    private UUID firstBloodPlayer;

    /**
     * Mutable player stats for efficient updates during gameplay.
     * Avoids object creation overhead from immutable record pattern.
     */

    public RoundData(int roundNumber, Collection<UUID> players) {
        this.stats = new HashMap<>(players.size());

        int startingLives = roundNumber <= 3 ? 3 : 1;
        for (UUID uuid : players) {
            stats.put(uuid, new PlayerRoundStats(startingLives));
        }
    }

    private PlayerRoundStats getStats(UUID player) {
        return stats.get(player);
    }

    public void addKill(UUID player) {
        PlayerRoundStats s = getStats(player);
        if (s != null) {
            s.incrementKills();
        }
    }

    public void addDamage(UUID player, double damage) {
        PlayerRoundStats s = getStats(player);
        if (s != null) {
            s.addDamage(damage);
        }
    }

    public void removeLife(UUID player) {
        PlayerRoundStats s = getStats(player);
        if (s != null) {
            s.decrementLives();
        }
    }

    public void setLastDamageTime(UUID player, long time) {
        PlayerRoundStats s = getStats(player);
        if (s != null) {
            s.setLastDamageTime(time);
        }
    }

    public boolean isAlive(UUID player) {
        PlayerRoundStats s = getStats(player);
        return s != null && s.isAlive();
    }

    public int getKills(UUID player) {
        PlayerRoundStats s = getStats(player);
        return s == null ? 0 : s.getKills();
    }

    public double getDamage(UUID player) {
        PlayerRoundStats s = getStats(player);
        return s == null ? 0.0 : s.getDamageDealt();
    }

    public long getLastDamageTime(UUID player) {
        PlayerRoundStats s = getStats(player);
        return s == null ? 0L : s.getLastDamageTime();
    }

    public void setFirstBloodPlayer(UUID player) {
        this.firstBloodPlayer = player;
    }

    public UUID getFirstBloodPlayer() {
        return firstBloodPlayer;
    }

    public void addSurvivor(UUID player) {
        PlayerRoundStats s = getStats(player);
        if (s != null) {
            s.markSurvivor();
        }
    }

    public UUID getMostKillsPlayer() {
        UUID best = null;
        int maxKills = -1;
        for (Map.Entry<UUID, PlayerRoundStats> entry : stats.entrySet()) {
            int kills = entry.getValue().getKills();
            if (kills > maxKills) {
                maxKills = kills;
                best = entry.getKey();
            }
        }
        return best;
    }

    public UUID getMostDamagePlayer() {
        UUID best = null;
        double maxDamage = -1.0;
        for (Map.Entry<UUID, PlayerRoundStats> entry : stats.entrySet()) {
            double damage = entry.getValue().getDamageDealt();
            if (damage > maxDamage) {
                maxDamage = damage;
                best = entry.getKey();
            }
        }
        return best;
    }
}

