package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.config.ConfigManager;
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
public class LeaderboardManager {

    private static LeaderboardManager instance;

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

    private LeaderboardManager() {}

    public static LeaderboardManager getInstance() {
        if (instance == null) instance = new LeaderboardManager();
        return instance;
    }

    public void start() {
        refresh();
        int minutes = ConfigManager.getInstance().getLeaderboardRefreshMinutes();
        if (minutes > 0) {
            task = SchedulerUtils.runTaskTimerAsync(this::refresh, 20L * 60 * minutes, 20L * 60 * minutes);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void refresh() {
        List<PlayerData> all = PlayerDataManager.getInstance().loadAllPlayers();
        int limit = ConfigManager.getInstance().getLeaderboardSize();
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
