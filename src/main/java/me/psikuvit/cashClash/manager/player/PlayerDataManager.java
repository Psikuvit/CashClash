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
        cache.values().forEach(d -> {
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
}
