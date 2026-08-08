package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks lobby inactivity and kicks players who stay AFK past a configurable timeout.
 * Activity is recorded by {@link me.psikuvit.cashClash.listener.lobby.AfkListener}; the
 * periodic check runs on the main thread so kicking is safe.
 */
public class AfkManager {

    private static AfkManager instance;

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();
    private final ConfigManager configManager;
    private final GameManager gameManager;
    private final MessagesConfig messagesConfig;

    public AfkManager(ConfigManager configManager, GameManager gameManager, MessagesConfig messagesConfig) {
        this.configManager = configManager;
        this.gameManager = gameManager;
        this.messagesConfig = messagesConfig;
        instance = this;
    }

    public static AfkManager getInstance() {
        return instance;
    }

    public void markActivity(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        warned.remove(uuid);
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        warned.remove(uuid);
    }

    /**
     * Kick players who have been idle in the lobby past the timeout. Runs periodically
     * from the main thread. A player is only considered when they are not in a game.
     */
    public void checkAndKick() {
        int timeoutMinutes = configManager.getAfkLobbyKickMinutes();
        if (timeoutMinutes <= 0) return;

        long timeoutMs = timeoutMinutes * 60_000L;
        int warningSeconds = configManager.getAfkWarningSeconds();
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gameManager.getPlayerSession(player) != null) continue;

            UUID uuid = player.getUniqueId();
            Long last = lastActivity.get(uuid);
            if (last == null) {
                lastActivity.put(uuid, now);
                continue;
            }

            long idleMs = now - last;
            if (idleMs >= timeoutMs) {
                Messages.send(player, "lobby-messages.afk-kick");
                player.kick(Messages.parse(
                        messagesConfig.getRaw("lobby-messages.afk-kick-reason")));
                remove(uuid);
            } else if (warningSeconds > 0 && idleMs >= timeoutMs - warningSeconds * 1000L && !warned.contains(uuid)) {
                warned.add(uuid);
                long remaining = (timeoutMs - idleMs) / 1000L;
                Messages.send(player, "lobby-messages.afk-kick-warning", "seconds", String.valueOf(remaining));
            }
        }
    }
}
