package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes leaderboards on an async worker so game logic is never slowed down.
 * The cached rankings are refreshed periodically and read synchronously on demand.
 */
public class LeaderboardManager implements Shutdownable {

    private final Map<LeaderboardType, List<PlayerData>> cached = new ConcurrentHashMap<>();
    private BukkitTask task;

    public enum LeaderboardType {
        WINS("wins"),
        PLAY_TIME("playtime"),
        COINS_EARNED("coins");

        private final String configKey;

        LeaderboardType(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }

        public static LeaderboardType fromString(String value) {
            if (value == null) return null;
            for (LeaderboardType type : values()) {
                if (type.configKey.equalsIgnoreCase(value)) return type;
            }
            return null;
        }
    }

    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;

    public LeaderboardManager(ConfigManager configManager, PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
    }

    public void start() {
        refresh();
        int minutes = configManager.getLeaderboardRefreshMinutes();
        if (minutes > 0) {
            task = SchedulerUtils.runTaskTimerAsync(this::refresh, 20L * 60 * minutes, 20L * 60 * minutes);
        }
    }

    @Override
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void refresh() {
        List<PlayerData> all = playerDataManager.loadAllPlayers();
        int limit = configManager.getLeaderboardSize();
        cached.put(LeaderboardType.WINS, sorted(all, Comparator.comparingInt(PlayerData::getWins).reversed(), limit));
        cached.put(LeaderboardType.PLAY_TIME, sorted(all, Comparator.comparingLong(PlayerData::getPlaytimeMillis).reversed(), limit));
        cached.put(LeaderboardType.COINS_EARNED, sorted(all, Comparator.comparingLong(PlayerData::getTotalCoinsEarned).reversed(), limit));
    }

    private List<PlayerData> sorted(List<PlayerData> all, Comparator<PlayerData> comparator, int limit) {
        List<PlayerData> result = new ArrayList<>(all);
        result.sort(comparator);
        if (result.size() > limit) result = result.subList(0, limit);
        return List.copyOf(result);
    }

    /**
     * Returns an immutable snapshot of the cached board.
     */
    public List<PlayerData> getBoard(LeaderboardType type) {
        return cached.getOrDefault(type, List.of());
    }
}
