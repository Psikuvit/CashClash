package me.psikuvit.cashClash.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Centralized API for managing all cooldowns across the plugin.
 * Supports player-based cooldowns with named abilities, and optional callbacks.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class CooldownManager {

    private static CooldownManager instance;

    // Maps: playerUUID -> (abilityKey -> cooldownEndTimeMillis)
    private final Map<UUID, Map<String, Long>> playerCooldowns;

    // Maps: playerUUID -> (abilityKey -> lastActionTimeMillis) for tracking time since events
    private final Map<UUID, Map<String, Long>> playerTimestamps;

    private CooldownManager() {
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.playerTimestamps = new ConcurrentHashMap<>();
    }

    public static CooldownManager getInstance() {
        if (instance == null) {
            instance = new CooldownManager();
        }
        return instance;
    }

    // ==================== COOLDOWN METHODS ====================

    /**
     * Set a cooldown for a player's ability.
     *
     * @param playerId  The player's UUID
     * @param ability   The unique ability key (e.g., "MAGIC_HELMET", "MEDIC_POUCH")
     * @param durationMs Duration of the cooldown in milliseconds
     */
    public void setCooldown(UUID playerId, String ability, long durationMs) {
        if (playerId == null || ability == null) return;
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(ability, System.currentTimeMillis() + durationMs);
    }

    /**
     * Set a cooldown for a player's ability in seconds.
     *
     * @param playerId      The player's UUID
     * @param ability       The unique ability key
     * @param durationSeconds Duration of the cooldown in seconds
     */
    public void setCooldownSeconds(UUID playerId, String ability, long durationSeconds) {
        setCooldown(playerId, ability, durationSeconds * 1000L);
    }

    /**
     * Check if a player's ability is currently on cooldown.
     *
     * @param playerId The player's UUID
     * @param ability  The ability key
     * @return true if on cooldown, false otherwise
     */
    public boolean isOnCooldown(UUID playerId, String ability) {
        if (playerId == null || ability == null) return false;

        Map<String, Long> abilities = playerCooldowns.get(playerId);
        if (abilities == null) return false;

        Long cooldownEnd = abilities.get(ability);
        if (cooldownEnd == null) return false;

        if (System.currentTimeMillis() >= cooldownEnd) {
            // Cooldown expired, clean it up
            abilities.remove(ability);
            return false;
        }
        return true;
    }

    /**
     * Get the remaining cooldown time in milliseconds.
     *
     * @param playerId The player's UUID
     * @param ability  The ability key
     * @return Remaining time in milliseconds, or 0 if not on cooldown
     */
    public long getRemainingCooldownMs(UUID playerId, String ability) {
        if (playerId == null || ability == null) return 0;

        Map<String, Long> abilities = playerCooldowns.get(playerId);
        if (abilities == null) return 0;

        Long cooldownEnd = abilities.get(ability);
        if (cooldownEnd == null) return 0;

        long remaining = cooldownEnd - System.currentTimeMillis();
        if (remaining <= 0) {
            abilities.remove(ability);
            return 0;
        }
        return remaining;
    }

    /**
     * Get the remaining cooldown time in seconds.
     *
     * @param playerId The player's UUID
     * @param ability  The ability key
     * @return Remaining time in seconds, or 0 if not on cooldown
     */
    public long getRemainingCooldownSeconds(UUID playerId, String ability) {
        return getRemainingCooldownMs(playerId, ability) / 1000L;
    }

    /**
     * Clear a specific cooldown for a player.
     *
     * @param playerId The player's UUID
     * @param ability  The ability key
     */
    public void clearCooldown(UUID playerId, String ability) {
        if (playerId == null || ability == null) return;

        Map<String, Long> abilities = playerCooldowns.get(playerId);
        if (abilities != null) {
            abilities.remove(ability);
        }
    }

    /**
     * Clear all cooldowns for a player.
     *
     * @param playerId The player's UUID
     */
    public void clearAllCooldowns(UUID playerId) {
        if (playerId == null) return;
        playerCooldowns.remove(playerId);
    }

    /**
     * Try to use an ability - returns true if not on cooldown (and sets cooldown), false if on cooldown.
     *
     * @param playerId      The player's UUID
     * @param ability       The ability key
     * @param cooldownMs    The cooldown to set if successful
     * @param onCooldown    Optional callback to run if on cooldown (receives remaining seconds)
     * @return true if ability can be used, false if on cooldown
     */
    public boolean tryUse(UUID playerId, String ability, long cooldownMs, Consumer<Long> onCooldown) {
        if (isOnCooldown(playerId, ability)) {
            if (onCooldown != null) {
                onCooldown.accept(getRemainingCooldownSeconds(playerId, ability));
            }
            return false;
        }
        setCooldown(playerId, ability, cooldownMs);
        return true;
    }

    /**
     * Try to use an ability with seconds-based cooldown.
     *
     * @param playerId        The player's UUID
     * @param ability         The ability key
     * @param cooldownSeconds The cooldown to set if successful (in seconds)
     * @param onCooldown      Optional callback to run if on cooldown
     * @return true if ability can be used, false if on cooldown
     */
    public boolean tryUseSeconds(UUID playerId, String ability, long cooldownSeconds, Consumer<Long> onCooldown) {
        return tryUse(playerId, ability, cooldownSeconds * 1000L, onCooldown);
    }

    // ==================== TIMESTAMP METHODS ====================

    /**
     * Record the current time as a timestamp for an event.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key (e.g., "LAST_DAMAGE", "SPAWN_TIME")
     */
    public void setTimestamp(UUID playerId, String key) {
        setTimestamp(playerId, key, System.currentTimeMillis());
    }

    /**
     * Record a specific timestamp.
     *
     * @param playerId  The player's UUID
     * @param key       The timestamp key
     * @param timestamp The timestamp value in milliseconds
     */
    public void setTimestamp(UUID playerId, String key, long timestamp) {
        if (playerId == null || key == null) return;
        playerTimestamps.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(key, timestamp);
    }

    /**
     * Get a recorded timestamp.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key
     * @return The timestamp, or 0 if not set
     */
    public long getTimestamp(UUID playerId, String key) {
        if (playerId == null || key == null) return 0;

        Map<String, Long> timestamps = playerTimestamps.get(playerId);
        if (timestamps == null) return 0;

        return timestamps.getOrDefault(key, 0L);
    }

    /**
     * Get the time elapsed since a timestamp was set.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key
     * @return Time elapsed in milliseconds, or -1 if no timestamp exists
     */
    public long getTimeSince(UUID playerId, String key) {
        long timestamp = getTimestamp(playerId, key);
        if (timestamp == 0) return -1;
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Get the time elapsed since a timestamp in seconds.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key
     * @return Time elapsed in seconds, or -1 if no timestamp exists
     */
    public long getTimeSinceSeconds(UUID playerId, String key) {
        long ms = getTimeSince(playerId, key);
        return ms < 0 ? -1 : ms / 1000L;
    }

    /**
     * Check if enough time has passed since a timestamp.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key
     * @param durationMs Required duration in milliseconds
     * @return true if the required time has passed (or no timestamp exists)
     */
    public boolean hasTimePassed(UUID playerId, String key, long durationMs) {
        long timestamp = getTimestamp(playerId, key);
        if (timestamp == 0) return true;
        return System.currentTimeMillis() - timestamp >= durationMs;
    }

    /**
     * Check if enough time has passed since a timestamp (in seconds).
     *
     * @param playerId      The player's UUID
     * @param key           The timestamp key
     * @param durationSeconds Required duration in seconds
     * @return true if the required time has passed
     */
    public boolean hasTimePassedSeconds(UUID playerId, String key, long durationSeconds) {
        return hasTimePassed(playerId, key, durationSeconds * 1000L);
    }

    /**
     * Clear a specific timestamp for a player.
     *
     * @param playerId The player's UUID
     * @param key      The timestamp key
     */
    public void clearTimestamp(UUID playerId, String key) {
        if (playerId == null || key == null) return;

        Map<String, Long> timestamps = playerTimestamps.get(playerId);
        if (timestamps != null) {
            timestamps.remove(key);
        }
    }

    /**
     * Clear all timestamps for a player.
     *
     * @param playerId The player's UUID
     */
    public void clearAllTimestamps(UUID playerId) {
        if (playerId == null) return;
        playerTimestamps.remove(playerId);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Clear all data for a player (cooldowns and timestamps).
     *
     * @param playerId The player's UUID
     */
    public void clearPlayer(UUID playerId) {
        clearAllCooldowns(playerId);
        clearAllTimestamps(playerId);
    }

    /**
     * Clear all data for a collection of players.
     *
     * @param playerIds Collection of player UUIDs
     */
    public void clearPlayers(Iterable<UUID> playerIds) {
        if (playerIds == null) return;
        for (UUID playerId : playerIds) {
            clearPlayer(playerId);
        }
    }

    /**
     * Clear all cooldowns and timestamps (full reset).
     */
    public void clearAll() {
        playerCooldowns.clear();
        playerTimestamps.clear();
    }

    // ==================== COMMON ABILITY KEYS ====================

    /**
     * Pre-defined cooldown keys for consistency across the plugin.
     */
    public static final class Keys {
        // Custom Armor
        public static final String MAGIC_HELMET = "MAGIC_HELMET";
        public static final String BUNNY_SHOES = "BUNNY_SHOES";
        public static final String GUARDIAN_VEST = "GUARDIAN_VEST";
        public static final String DRAGON_DOUBLE_JUMP = "DRAGON_DOUBLE_JUMP";

        // Custom Items
        public static final String MEDIC_POUCH = "MEDIC_POUCH";
        public static final String INVIS_CLOAK = "INVIS_CLOAK";
        public static final String CONSUMABLE = "CONSUMABLE";

        // Mythic Items
        public static final String COIN_CLEAVER_GRENADE = "COIN_CLEAVER_GRENADE";
        public static final String CARLS_BATTLEAXE_SLASH = "CARLS_BATTLEAXE_SLASH";
        public static final String CARLS_BATTLEAXE_CRIT = "CARLS_BATTLEAXE_CRIT";
        public static final String WIND_BOW_BOOST = "WIND_BOW_BOOST";
        public static final String BOBBY_DOG = "BOBBY_DOG";
        public static final String ELECTRIC_EEL_LIGHTNING = "ELECTRIC_EEL_LIGHTNING";
        public static final String ELECTRIC_EEL_CHAIN = "ELECTRIC_EEL_CHAIN";
        public static final String GOBLIN_SPEAR_THROW = "GOBLIN_SPEAR_THROW";
        public static final String SANDSTORMER_RELOAD = "SANDSTORMER_RELOAD";
        public static final String WARDEN_SHOCKWAVE = "WARDEN_SHOCKWAVE";
        public static final String WARDEN_MELEE = "WARDEN_MELEE";
        public static final String BLAZEBITE_RELOAD = "BLAZEBITE_RELOAD";

        // Timestamps
        public static final String LAST_DAMAGE = "LAST_DAMAGE";
        public static final String LAST_DAMAGE_DEALT = "LAST_DAMAGE_DEALT";
        public static final String SPAWN_TIME = "SPAWN_TIME";
        public static final String CLOSE_CALL_HEAL = "CLOSE_CALL_HEAL";
        public static final String TAX_EVASION_MINUTE = "TAX_EVASION_MINUTE";
        public static final String DEATHMAULER_DAMAGE = "DEATHMAULER_DAMAGE";
        public static final String SANDSTORMER_CHARGE = "SANDSTORMER_CHARGE";
        public static final String LAST_MOVE = "LAST_MOVE";

        private Keys() {} // Prevent instantiation
    }
}

