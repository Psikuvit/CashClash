package me.psikuvit.cashClash.rank;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
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
 * Staff ranks: who is an owner or an admin, and what each rank may do. A rank is set with
 * {@code /cc rank} and saved with the player's data. Both ranks are server operators - op is
 * re-applied on every join, so the saved rank stays the source of truth - and owners also get
 * {@code cashclash.owner}, which the owner-only commands require. Ranks don't change how a name
 * looks; that's left to a dedicated rank/chat plugin.
 */
public class RankManager implements Listener {

    public static final String OWNER_PERMISSION = "cashclash.owner";

    private final PlayerDataManager playerDataManager;
    private final Map<UUID, PermissionAttachment> ownerAttachments;

    public RankManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
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
        Player online = target.getPlayer();
        if (online != null) {
            apply(online);
            online.updateCommands();
        }
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
     * Puts a player's rank on them: op and the owner permission.
     */
    private void apply(Player player) {
        Rank rank = getRank(player);
        if (rank != null && !player.isOp()) player.setOp(true);

        PermissionAttachment attachment = ownerAttachments.remove(player.getUniqueId());
        if (attachment != null) attachment.remove();
        if (rank == Rank.OWNER) {
            ownerAttachments.put(player.getUniqueId(), player.addAttachment(CashClashPlugin.getInstance(), OWNER_PERMISSION, true));
        }
    }
}
