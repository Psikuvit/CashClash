package me.psikuvit.cashClash.rank;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.TabListManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff ranks: who is an owner or an admin, the tag shown before their name in chat and the tab
 * list, and what each rank may do. A rank is set with {@code /cc rank} and saved with the
 * player's data. Both ranks are server operators - op is re-applied on every join, so the saved
 * rank stays the source of truth - and owners also get {@code cashclash.owner}, which the
 * owner-only commands require.
 */
public class RankManager implements Listener {

    public static final String OWNER_PERMISSION = "cashclash.owner";

    private final MessagesConfig messagesConfig;
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;
    private final TabListManager tabListManager;
    private final Map<UUID, PermissionAttachment> ownerAttachments;

    public RankManager(MessagesConfig messagesConfig, GameManager gameManager, PlayerDataManager playerDataManager,
                       TabListManager tabListManager) {
        this.messagesConfig = messagesConfig;
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
        this.tabListManager = tabListManager;
        this.ownerAttachments = new HashMap<>();
    }

    /**
     * A player's saved rank, or null for none.
     */
    public Rank getRank(OfflinePlayer player) {
        return playerDataManager.readData(player.getUniqueId()).map(PlayerData::getRank).orElse(null);
    }

    /**
     * Gives a player a rank (null takes it away) and saves it, online or not. A rank makes them
     * op; taking it away takes op too.
     *
     * @return false if their data couldn't be read or saved - nothing changed then
     */
    public boolean setRank(OfflinePlayer target, Rank rank) {
        Optional<PlayerData> data = playerDataManager.readData(target.getUniqueId());
        if (data.isEmpty()) return false;

        Rank previous = data.get().getRank();
        data.get().setRank(rank);
        if (!playerDataManager.saveData(data.get())) {
            data.get().setRank(previous);
            return false;
        }

        target.setOp(rank != null);
        if (target.getPlayer() != null) refresh(target.getPlayer());
        return true;
    }

    /**
     * Every player with a rank, by UUID.
     */
    public Map<UUID, Rank> getRankedPlayers() {
        Map<UUID, Rank> ranked = new LinkedHashMap<>();
        for (PlayerData data : playerDataManager.loadAllPlayers()) {
            if (data.getRank() != null) ranked.put(data.getUuid(), data.getRank());
        }
        return ranked;
    }

    /**
     * The rank tag followed by a space, ready to go in front of a name - empty for no rank.
     */
    public Component tag(Player player) {
        Rank rank = getRank(player);
        if (rank == null) return Component.empty();
        return Messages.parse(messagesConfig.getRaw(rank.getTagMessageKey())).append(Component.space());
    }

    /**
     * LOWEST so the tag is on the player's name before anything else sets up their tab entry.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    /**
     * An owner joining is announced to everyone where they land, a few ticks in so it's after
     * the lobby teleport.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (getRank(player) != Rank.OWNER) return;

        SchedulerUtils.runTaskLater(() -> {
            if (!player.isOnline()) return;
            List<UUID> audience = player.getWorld().getPlayers().stream().map(Player::getUniqueId).toList();
            Messages.broadcast(audience, "ranks.owner-joined", "player", player.getName());
            SoundUtils.playTo(audience, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            ParticleUtils.totem(player.getLocation().add(0, 1, 0), 40, 0.6);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = ownerAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) attachment.remove();
    }

    /**
     * Puts a player's rank on them: op, the owner permission, and the tag on their display name
     * (which chat, death messages and the tab list show).
     */
    private void apply(Player player) {
        Rank rank = getRank(player);
        if (rank != null && !player.isOp()) player.setOp(true);

        PermissionAttachment attachment = ownerAttachments.remove(player.getUniqueId());
        if (attachment != null) attachment.remove();
        if (rank == Rank.OWNER) {
            ownerAttachments.put(player.getUniqueId(), player.addAttachment(CashClashPlugin.getInstance(), OWNER_PERMISSION, true));
        }

        player.displayName(tag(player).append(Component.text(player.getName())));
    }

    /**
     * {@link #apply} plus the tab entry, for a rank given or taken while the player is online.
     */
    private void refresh(Player player) {
        apply(player);
        GameSession session = gameManager.getPlayerSession(player);
        if (session != null) {
            tabListManager.setPlayerToSession(player, session);
        } else {
            tabListManager.setPlayerToLobby(player);
        }
        player.updateCommands();
    }
}
