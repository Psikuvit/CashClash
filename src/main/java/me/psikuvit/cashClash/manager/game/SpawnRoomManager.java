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
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Each team's spawn room, marked per map with /cc template. Nobody takes or deals damage inside
 * one, and during combat a team heals in its own room.
 */
public class SpawnRoomManager implements Shutdownable {

    private static final long HEAL_PERIOD_TICKS = 20L;

    private final GameManager gameManager;
    private final ConfigManager configManager;
    private BukkitTask healTask;

    public SpawnRoomManager(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
    }

    public void start() {
        healTask = SchedulerUtils.runTaskTimer(this::healInOwnRooms, HEAL_PERIOD_TICKS, HEAL_PERIOD_TICKS);
    }

    /**
     * A team's spawn room in a game, with the doorways into it.
     */
    record SpawnRoom(TeamColor team, BlockRegion room, List<BlockRegion> doors) {
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

    @Override
    public void shutdown() {
        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
    }
}
