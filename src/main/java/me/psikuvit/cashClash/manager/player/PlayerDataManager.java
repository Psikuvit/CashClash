package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.storage.DatabaseProvider;
import me.psikuvit.cashClash.storage.MySQLProvider;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.storage.SQLiteProvider;
import me.psikuvit.cashClash.util.Messages;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager responsible for caching player data and delegating persistence
 * to a configured DatabaseProvider.
 */
public class PlayerDataManager {

    private static PlayerDataManager instance;

    private final Map<UUID, PlayerData> cache;
    private final DatabaseProvider provider;

    private PlayerDataManager(DatabaseProvider provider) {
        this.provider = provider;
        this.cache = new ConcurrentHashMap<>();
    }

    public static void init(CashClashPlugin plugin) throws SQLException {
        if (instance != null) return;

        // pick provider from config
        var cfg = plugin.getConfig();
        String type = cfg.getString("storage.type", "sqlite").toLowerCase();
        if (type.equals("mysql")) {
            String url = cfg.getString("storage.mysql.url");
            String user = cfg.getString("storage.mysql.user");
            String pass = cfg.getString("storage.mysql.pass");
            instance = new PlayerDataManager(new MySQLProvider(url, user, pass));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "players.db");
            instance = new PlayerDataManager(new SQLiteProvider(dbFile));
        }

        instance.provider.init();
    }

    public static PlayerDataManager getInstance() {
        return instance;
    }

    public Optional<PlayerData> getCached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    /**
     * Load PlayerData from cache or storage. If not present, create a new default PlayerData.
     */
    public PlayerData getOrLoadData(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            try {
                Optional<PlayerData> opt = provider.loadPlayer(id);
                if (opt.isPresent()) return opt.get();
            } catch (SQLException e) {
                Messages.debug("Failed to load player from DB: " + e.getMessage());
            }
            return new PlayerData(uuid);
        });
    }

    public void shutdown() {
        long now = System.currentTimeMillis();
        cache.values().forEach(d -> {
            accumulatePlaytime(d, now);
            try {
                provider.savePlayer(d);
            } catch (SQLException e) {
                Messages.debug("Failed to save player during shutdown: " + e.getMessage());
            }
        });

        try {
            provider.close();
        } catch (IOException e) {
            Messages.debug("Failed to close DB provider: " + e.getMessage());
        }
    }

    /**
     * Records the moment a player came online so their playtime can be accumulated.
     */
    public void markJoined(UUID uuid, long now) {
        PlayerData d = getOrLoadData(uuid);
        if (d.getLastSeen() <= 0) d.setLastSeen(now);
    }

    /**
     * Accumulates playtime since the player joined, persists the data and drops it
     * from the cache. Returns the amount of playtime accumulated in milliseconds.
     */
    public long markLeft(UUID uuid, long now) {
        PlayerData d = getOrLoadData(uuid);
        long played = accumulatePlaytime(d, now);
        try {
            provider.savePlayer(d);
        } catch (SQLException e) {
            Messages.debug("Failed to save player on quit: " + e.getMessage());
        }
        cache.remove(uuid);
        return played;
    }

    /**
     * Adds the online duration since the last markJoined call to the player's total
     * playtime and resets the session marker.
     */
    private long accumulatePlaytime(PlayerData d, long now) {
        long last = d.getLastSeen();
        long played = 0L;
        if (last > 0 && now > last) {
            played = now - last;
            d.addPlaytimeMillis(played);
        }
        d.setLastSeen(0L);
        return played;
    }

    /**
     * Loads all stored players (used by the leaderboard worker). Online players are
     * read from the cache so their latest values are included. Cache entries take
     * precedence over the stored copies for the same UUID.
     */
    public List<PlayerData> loadAllPlayers() {
        Map<UUID, PlayerData> merged = new ConcurrentHashMap<>();
        try {
            List<PlayerData> stored = provider.loadAllPlayers();
            for (PlayerData data : stored) {
                if (data != null && data.getUuid() != null) merged.putIfAbsent(data.getUuid(), data);
            }
        } catch (SQLException e) {
            Messages.debug("Failed to load all players from DB: " + e.getMessage());
        }
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }
        return new ArrayList<>(merged.values());
    }

    public PlayerData getData(UUID uuid) {
        return getOrLoadData(uuid);
    }

    public void incWins(UUID uuid) {
        PlayerData d = getOrLoadData(uuid);
        d.incWins();
    }

    public void incDeaths(UUID uuid) {
        PlayerData d = getOrLoadData(uuid);
        d.incDeaths();
    }

    public void incKills(UUID uuid) {
        PlayerData d = getOrLoadData(uuid);
        d.incKills();
    }

    public void addInvestedCoins(UUID uuid, long amount) {
        PlayerData d = getOrLoadData(uuid);
        d.addInvestedCoins(amount);
    }

    public void addEarnedCoins(UUID uuid, long amount) {
        PlayerData d = getOrLoadData(uuid);
        d.addEarnedCoins(amount);
    }
}
