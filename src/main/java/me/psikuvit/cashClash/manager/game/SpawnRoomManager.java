package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.BlockRegion;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.enums.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Each team's spawn room, marked per map with /cc template. Nobody takes or deals damage inside
 * one, and during combat a team heals in its own room.
 *
 * <p>A room's doors are invisible walls only for the players they stop: during the buy phase
 * they keep each team in its own room, and during combat nobody gets back in once they're out
 * (either room). Those players are sent fake barrier blocks in the doorway - nobody else's client
 * has them, so a player still inside walks straight out - and a move across the room's edge is
 * refused server-side too, so a client ignoring the barriers gets nowhere. Walking into a door
 * from outside shows it as red glass to that player for a moment; a player inside never sees
 * it.</p>
 */
public class SpawnRoomManager implements Listener, Shutdownable {

    private static final long TICK_PERIOD = 5L;
    private static final long HEAL_EVERY_TICKS = 20L;
    // The client drops fake blocks whenever it reloads the chunk, so they're re-sent this often
    private static final long DOOR_RESEND_MS = 1000L;
    // How close to a door's face counts as walking into it
    private static final double DOOR_TOUCH_MARGIN = 0.15;

    private final GameManager gameManager;
    private final ConfigManager configManager;
    private final Map<UUID, DoorView> doorViews;
    private BukkitTask task;
    private long ticks;

    public SpawnRoomManager(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
        this.doorViews = new HashMap<>();
    }

    public void start() {
        task = SchedulerUtils.runTaskTimer(this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /**
     * A team's spawn room in a game, with the doorways into it.
     */
    record SpawnRoom(TeamColor team, BlockRegion room, List<BlockRegion> doors) {
    }

    /**
     * The doors one player's client is currently shown: whether each is shown flashing, when it
     * was last sent, and until when it flashes.
     */
    private static final class DoorView {
        private World world;
        private final Map<BlockRegion, Boolean> shownFlashing = new HashMap<>();
        private final Map<BlockRegion, Long> sentAt = new HashMap<>();
        private final Map<BlockRegion, Long> flashUntil = new HashMap<>();

        private void forget(BlockRegion door) {
            shownFlashing.remove(door);
            sentAt.remove(door);
            flashUntil.remove(door);
        }
    }

    List<SpawnRoom> roomsOf(GameSession session) {
        TemplateWorld template = session.getArenaTemplate();
        if (template == null) return List.of();

        List<SpawnRoom> rooms = new ArrayList<>(2);
        for (TeamColor team : TeamColor.values()) {
            BlockRegion room = template.getSpawnRoom(team);
            if (room != null) rooms.add(new SpawnRoom(team, room, template.getSpawnRoomDoors(team)));
        }
        return rooms;
    }

    /**
     * @return the team whose spawn room the location is in, or null
     */
    public TeamColor roomTeamAt(GameSession session, Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(session.getGameWorld())) return null;
        for (SpawnRoom room : roomsOf(session)) {
            if (room.room().contains(loc)) return room.team();
        }
        return null;
    }

    public boolean isInSpawnRoom(Player player) {
        GameSession session = gameManager.getPlayerSession(player);
        return session != null && roomTeamAt(session, player.getLocation()) != null;
    }

    private void tick() {
        ticks += TICK_PERIOD;
        if (ticks % HEAL_EVERY_TICKS == 0) healInOwnRooms();
        updateDoors();
    }

    private void healInOwnRooms() {
        double amount = configManager.getSpawnRoomHealPerSecond();
        if (amount <= 0) return;

        for (GameSession session : gameManager.getActiveSessions()) {
            if (session.getState() != GameState.COMBAT) continue;
            for (UUID uuid : session.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || CashClashPlayer.isPlayerDead(player)) continue;

                Team team = session.getPlayerTeam(player);
                TeamColor roomTeam = roomTeamAt(session, player.getLocation());
                if (team != null && roomTeam != null && roomTeam.getTeamNumber() == team.getTeamNumber()) {
                    CashClashPlayer.heal(player, amount);
                }
            }
        }
    }

    // ==================== DOORS ====================

    private void updateDoors() {
        Set<UUID> inGame = new HashSet<>();
        for (GameSession session : gameManager.getActiveSessions()) {
            List<SpawnRoom> rooms = roomsOf(session);
            for (UUID uuid : session.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                inGame.add(uuid);
                updateDoors(session, rooms, player);
            }
        }

        doorViews.entrySet().removeIf(entry -> {
            if (inGame.contains(entry.getKey())) return false;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) hideAllDoors(player, entry.getValue());
            return true;
        });
    }

    private void updateDoors(GameSession session, List<SpawnRoom> rooms, Player player) {
        DoorView view = doorViews.computeIfAbsent(player.getUniqueId(), k -> new DoorView());
        if (view.world != player.getWorld()) {
            // A new world means the client already dropped every fake block.
            view.shownFlashing.clear();
            view.sentAt.clear();
            view.flashUntil.clear();
            view.world = player.getWorld();
        }

        long now = System.currentTimeMillis();
        Location loc = player.getLocation();
        double viewDistance = configManager.getSpawnRoomDoorViewDistance();
        boolean inGameWorld = player.getWorld().equals(session.getGameWorld());

        for (SpawnRoom room : rooms) {
            boolean barred = inGameWorld && isBarred(session, room, player);
            for (BlockRegion door : room.doors()) {
                if (!barred || door.centerDistanceSquared(loc) > viewDistance * viewDistance) {
                    if (view.shownFlashing.containsKey(door)) sendDoor(player, door, null);
                    view.forget(door);
                    continue;
                }

                if (!isInside(room, loc) && isTouching(player, door)) flash(view, door, now);
                boolean flashing = view.flashUntil.getOrDefault(door, 0L) > now;
                Boolean shown = view.shownFlashing.get(door);
                if (shown == null || shown != flashing || now - view.sentAt.getOrDefault(door, 0L) >= DOOR_RESEND_MS) {
                    sendDoor(player, door, flashing ? Material.RED_STAINED_GLASS : Material.BARRIER);
                    view.shownFlashing.put(door, flashing);
                    view.sentAt.put(door, now);
                }
            }
        }
    }

    /**
     * Whether a room's doors stop this player right now: in the buy phase they keep a team in
     * its own room; in combat they stop anyone outside from coming in. They never trap someone
     * already inside during combat.
     */
    private boolean isBarred(GameSession session, SpawnRoom room, Player player) {
        if (CashClashPlayer.isPlayerDead(player) || player.getGameMode() == GameMode.SPECTATOR) return false;
        Team team = session.getPlayerTeam(player);
        if (team == null) return false;

        boolean inside = isInside(room, player.getLocation());
        return switch (session.getState()) {
            case SHOPPING, BUFF_SELECTION -> inside && team.getTeamNumber() == room.team().getTeamNumber();
            case COMBAT -> !inside;
            default -> false;
        };
    }

    /**
     * In the room or standing in one of its doorways.
     */
    private static boolean isInside(SpawnRoom room, Location loc) {
        if (room.room().contains(loc)) return true;
        for (BlockRegion door : room.doors()) {
            if (door.contains(loc)) return true;
        }
        return false;
    }

    private static boolean isTouching(Player player, BlockRegion door) {
        return player.getBoundingBox().expand(DOOR_TOUCH_MARGIN).overlaps(door.toBoundingBox());
    }

    private void flash(DoorView view, BlockRegion door, long now) {
        view.flashUntil.merge(door, now + configManager.getSpawnRoomDoorFlashSeconds() * 1000L, Math::max);
    }

    /**
     * Shows the door's open blocks to the player as {@code fake}, or as what's really there when
     * {@code fake} is null. Solid blocks in the doorway (its frame) are left alone.
     */
    private static void sendDoor(Player player, BlockRegion door, Material fake) {
        World world = player.getWorld();
        BlockData fakeData = fake != null ? fake.createBlockData() : null;
        for (int x = door.minX(); x <= door.maxX(); x++) {
            for (int y = door.minY(); y <= door.maxY(); y++) {
                for (int z = door.minZ(); z <= door.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (fakeData == null) {
                        player.sendBlockChange(block.getLocation(), block.getBlockData());
                    } else if (!block.getType().isSolid()) {
                        player.sendBlockChange(block.getLocation(), fakeData);
                    }
                }
            }
        }
    }

    /**
     * Puts the real blocks back for a player leaving the game - unless they've already changed
     * world, where those coordinates mean something else and the client dropped the fakes anyway.
     */
    private static void hideAllDoors(Player player, DoorView view) {
        if (!player.getWorld().equals(view.world)) return;
        for (BlockRegion door : view.shownFlashing.keySet()) {
            sendDoor(player, door, null);
        }
    }

    /**
     * Refuses a move across a room's edge for a player its doors stop - the barriers already stop
     * an honest client, so this only catches one that ignores them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null || !player.getWorld().equals(session.getGameWorld())) return;

        for (SpawnRoom room : roomsOf(session)) {
            if (isInside(room, from) == isInside(room, to) || !isBarred(session, room, player)) continue;

            event.setCancelled(true);
            DoorView view = doorViews.get(player.getUniqueId());
            if (view != null && !isInside(room, from)) {
                long now = System.currentTimeMillis();
                for (BlockRegion door : room.doors()) {
                    if (isTouching(player, door)) flash(view, door, now);
                }
            }
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        doorViews.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        doorViews.forEach((uuid, view) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) hideAllDoors(player, view);
        });
        doorViews.clear();
    }
}
