package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.event.PlayerBackToGameEvent;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Green teammate outlines that only the viewer's own team can see. Vanilla glowing is one flag
 * per entity that every viewer receives, so instead of glowing teammates for real, the
 * entity-metadata packets going to each viewer get the glowing bit set for that viewer's
 * teammates. Glowing the server applies for real (presidents, flag carriers, item effects) is
 * left alone and still shows for everyone. The outline's colour comes from the viewer's own
 * scoreboard, where their teammates sit in the green glow team - except anyone the gamemode keeps
 * in their team colour ({@code Gamemode#keepsTeamColorOutline}, PTP's presidents).
 * <p>
 * Outlines are on from combat start until the round's combat ends.
 */
public class TeamOutlineManager implements PacketListener, Listener {

    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte ON_FIRE = 0x01;
    private static final byte SNEAKING = 0x02;
    private static final byte SPRINTING = 0x08;
    private static final byte SWIMMING = 0x10;
    private static final byte INVISIBLE = 0x20;
    private static final byte GLOWING = 0x40;
    private static final byte GLIDING = (byte) 0x80;

    private final GameManager gameManager;
    private final Set<UUID> activeSessions;
    // viewer -> (outlined teammate -> that teammate's entity id); read on the netty threads
    private final Map<UUID, Map<UUID, Integer>> outlines;

    public TeamOutlineManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.activeSessions = ConcurrentHashMap.newKeySet();
        this.outlines = new ConcurrentHashMap<>();
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    /**
     * Turns outlines on for a session: every player sees their own teammates outlined in green.
     */
    public void showOutlines(GameSession session) {
        activeSessions.add(session.getSessionId());
        for (UUID viewerId : session.getPlayers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) continue;
            for (Player teammate : onlineTeammates(session, viewer)) {
                addOutline(session, viewer, teammate);
            }
        }
    }

    /**
     * Turns outlines off for a session and tells every viewer their teammates' real flags again.
     */
    public void clearOutlines(GameSession session) {
        activeSessions.remove(session.getSessionId());
        for (UUID viewerId : session.getPlayers()) {
            Map<UUID, Integer> targets = outlines.remove(viewerId);
            Player viewer = Bukkit.getPlayer(viewerId);
            if (targets == null || viewer == null || !viewer.isOnline()) continue;
            for (UUID targetId : targets.keySet()) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) sendFlags(viewer, target);
            }
        }
    }

    /**
     * A player coming back from death or a disconnect gets both directions back: their
     * teammates outlined for them, and themselves outlined for their teammates.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBackInGame(PlayerBackToGameEvent event) {
        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null || !activeSessions.contains(session.getSessionId())) return;

        for (Player teammate : onlineTeammates(session, player)) {
            addOutline(session, player, teammate);
            addOutline(session, teammate, player);
        }
    }

    /**
     * When an outlined teammate comes into a viewer's tracking range, the spawn packets don't
     * include the entity flags if they're all clear, so there's nothing for
     * {@link #onPacketSend} to mark. Send them a tick later, once the client knows the entity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        Player viewer = event.getPlayer();
        if (!(event.getEntity() instanceof Player target)) return;

        Map<UUID, Integer> targets = outlines.get(viewer.getUniqueId());
        if (targets == null || !targets.containsKey(target.getUniqueId())) return;

        targets.put(target.getUniqueId(), target.getEntityId());
        SchedulerUtils.runTask(() -> {
            if (viewer.isOnline() && target.isOnline()) sendFlags(viewer, target);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        outlines.remove(playerId);
        outlines.values().forEach(targets -> targets.remove(playerId));
    }

    /**
     * Sets the glowing bit on metadata packets about a viewer's outlined teammates, leaving every
     * other flag as the server sent it. Runs on the netty threads - no Bukkit calls here.
     */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        UUID viewerId = event.getUser().getUUID();
        Map<UUID, Integer> targets = viewerId != null ? outlines.get(viewerId) : null;
        if (targets == null || targets.isEmpty()) return;

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        if (!targets.containsValue(packet.getEntityId())) return;

        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getIndex() == ENTITY_FLAGS_INDEX && data.getValue() instanceof Byte flags) {
                setByteValue(data, (byte) (flags | GLOWING));
                event.markForReEncode(true);
            }
        }
    }

    private void addOutline(GameSession session, Player viewer, Player target) {
        if (session.getGamemode() != null && session.getGamemode().keepsTeamColorOutline(target.getUniqueId())) {
            TeamColorUtils.assignTeamColor(viewer.getScoreboard(), target, session);
        } else {
            TeamColorUtils.addToGreenGlowTeam(viewer.getScoreboard(), target);
        }
        outlines.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(target.getUniqueId(), target.getEntityId());
        sendFlags(viewer, target);
    }

    /**
     * Sends the viewer the target's current entity flags, with the glowing bit added if the target
     * is outlined for them. The server only resends flags when one changes, so this is how an
     * outline appears or disappears straight away.
     */
    private void sendFlags(Player viewer, Player target) {
        byte flags = currentFlags(target);
        Map<UUID, Integer> targets = outlines.get(viewer.getUniqueId());
        if (targets != null && targets.containsKey(target.getUniqueId())) {
            flags |= GLOWING;
        }

        List<EntityData<?>> metadata = List.of(new EntityData<>(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags));
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(target.getEntityId(), metadata));
    }

    /**
     * The target's entity-flags byte as the server itself would send it.
     */
    private static byte currentFlags(Player target) {
        byte flags = 0;
        if (target.getFireTicks() > 0) flags |= ON_FIRE;
        if (target.isSneaking()) flags |= SNEAKING;
        if (target.isSprinting()) flags |= SPRINTING;
        if (target.isSwimming()) flags |= SWIMMING;
        if (target.isInvisible()) flags |= INVISIBLE;
        if (target.isGlowing() || target.hasPotionEffect(PotionEffectType.GLOWING)) flags |= GLOWING;
        if (target.isGliding()) flags |= GLIDING;
        return flags;
    }

    private List<Player> onlineTeammates(GameSession session, Player player) {
        Team team = session.getPlayerTeam(player);
        if (team == null) return List.of();

        List<Player> teammates = new ArrayList<>();
        for (UUID teammateId : team.getPlayers()) {
            if (teammateId.equals(player.getUniqueId())) continue;
            Player teammate = Bukkit.getPlayer(teammateId);
            if (teammate != null && teammate.isOnline()) teammates.add(teammate);
        }
        return teammates;
    }

    @SuppressWarnings("unchecked")
    private static void setByteValue(EntityData<?> data, byte value) {
        ((EntityData<Byte>) data).setValue(value);
    }
}
