package me.psikuvit.cashClash.game.round;

import me.psikuvit.cashClash.game.CashQuakeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks data for the current round
 */
public class RoundData {

    private final int roundNumber;
    // consolidated per-player stats record
    private final Map<UUID, PlayerRoundStats> stats;
    private UUID firstBloodPlayer;
    private final List<CashQuakeEvent> activeEvents;

    public record PlayerRoundStats(int kills, double damageDealt, int lives, long lastDamageTime, boolean survivor) {
        public PlayerRoundStats withKill() {
            return new PlayerRoundStats(kills + 1, damageDealt, lives, lastDamageTime, survivor);
        }

        public PlayerRoundStats withDamage(double additional) {
            return new PlayerRoundStats(kills, damageDealt + additional, lives, lastDamageTime, survivor);
        }

        public PlayerRoundStats removeLife() {
            return new PlayerRoundStats(kills, damageDealt, Math.max(0, lives - 1), lastDamageTime, survivor);
        }

        public PlayerRoundStats withLives(int newLives) {
            return new PlayerRoundStats(kills, damageDealt, newLives, lastDamageTime, survivor);
        }

        public PlayerRoundStats withLastDamageTime(long time) {
            return new PlayerRoundStats(kills, damageDealt, lives, time, survivor);
        }

        public PlayerRoundStats withSurvivor() {
            return new PlayerRoundStats(kills, damageDealt, lives, lastDamageTime, true);
        }

    }

    public RoundData(int roundNumber, Collection<UUID> players) {
        this.roundNumber = roundNumber;
        this.stats = new HashMap<>();
        this.activeEvents = new ArrayList<>();

        // Initialize lives based on round (rounds 1-3: 3 lives, rounds 4-5: 1 life)
        int startingLives = roundNumber <= 3 ? 3 : 1;
        for (UUID uuid : players) {
            stats.put(uuid, new PlayerRoundStats(0, 0.0, startingLives, 0L, false));
        }
    }

    public void addKill(UUID player) {
        stats.computeIfPresent(player, (k, v) -> v.withKill());
    }

    public void addDamage(UUID player, double damage) {
        stats.computeIfPresent(player, (k, v) -> v.withDamage(damage));
    }

    public void removeLife(UUID player) {
        stats.computeIfPresent(player, (k, v) -> v.removeLife());
    }

    public void setLastDamageTime(UUID player, long time) {
        stats.computeIfPresent(player, (k, v) -> v.withLastDamageTime(time));
    }

    public boolean isAlive(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s != null && s.lives() > 0;
    }

    public int getLives(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s == null ? 0 : s.lives();
    }

    public int getKills(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s == null ? 0 : s.kills();
    }

    public double getDamage(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s == null ? 0.0 : s.damageDealt();
    }

    public long getLastDamageTime(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s == null ? 0L : s.lastDamageTime();
    }

    public void setFirstBloodPlayer(UUID player) {
        this.firstBloodPlayer = player;
    }

    public UUID getFirstBloodPlayer() {
        return firstBloodPlayer;
    }

    public void addSurvivor(UUID player) {
        stats.computeIfPresent(player, (k, v) -> v.withSurvivor());
    }

    public boolean isSurvivor(UUID player) {
        PlayerRoundStats s = stats.get(player);
        return s != null && s.survivor();
    }

    public void addEvent(CashQuakeEvent event) {
        activeEvents.add(event);
    }

    public List<CashQuakeEvent> getActiveEvents() {
        return new ArrayList<>(activeEvents);
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public UUID getMostKillsPlayer() {
        return stats.entrySet().stream()
            .max(Comparator.comparingInt(a -> a.getValue().kills()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    public UUID getMostDamagePlayer() {
        return stats.entrySet().stream()
            .max(Comparator.comparingDouble(a -> a.getValue().damageDealt()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}

