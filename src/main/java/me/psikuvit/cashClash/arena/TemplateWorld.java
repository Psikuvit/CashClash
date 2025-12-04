package me.psikuvit.cashClash.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds information about a template world used by arenas (template id -> world name)
 */
public class TemplateWorld {

    private final String id;
    private World world;

    private Location lobbySpawn;
    private Location spectatorSpawn;

    private final List<Location> team1Spawns;
    private final List<Location> team2Spawns;

    // Shop spawn points for each team (template-space)
    private Location team1ShopSpawn;
    private Location team2ShopSpawn;
    private final List<Location> villagersSpawnPoint;

    public TemplateWorld(String id, World world) {
        this.id = id;
        this.world = world;
        this.team1Spawns = new ArrayList<>();
        this.team2Spawns = new ArrayList<>();
        this.villagersSpawnPoint = new ArrayList<>();

    }

    public String getId() {
        return id;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setSpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location loc) {
        this.spectatorSpawn = loc;
    }

    public void addTeam1Spawn(Location loc) {
        if (team1Spawns.size() < 4) team1Spawns.add(loc);
    }
    public void addTeam2Spawn(Location loc) {
        if (team2Spawns.size() < 4) team2Spawns.add(loc);
    }

    public void setTeam1Spawn(int idx, Location loc) {
        ensureListSize(team1Spawns, idx + 1);
        team1Spawns.set(idx, loc);
    }

    public void setTeam2Spawn(int idx, Location loc) {
        ensureListSize(team2Spawns, idx + 1);
        team2Spawns.set(idx, loc);
    }

    private void ensureListSize(List<Location> list, int size) {
        while (list.size() < size) list.add(null);
    }

    public Location getTeam1Spawn(int idx) {
        if (idx < 0 || idx >= team1Spawns.size()) return null;
        return team1Spawns.get(idx);
    }

    public Location getTeam2Spawn(int idx) {
        if (idx < 0 || idx >= team2Spawns.size()) return null;
        return team2Spawns.get(idx);
    }

    public List<Location> getTeam1Spawns() {
        return new ArrayList<>(team1Spawns);
    }

    public List<Location> getTeam2Spawns() {
        return new ArrayList<>(team2Spawns);
    }

    public Location getTeam1ShopSpawn() {
        return team1ShopSpawn;
    }

    public void setTeam1ShopSpawn(Location team1ShopSpawn) {
        this.team1ShopSpawn = team1ShopSpawn;
    }

    public Location getTeam2ShopSpawn() {
        return team2ShopSpawn;
    }
    public void setTeam2ShopSpawn(Location team2ShopSpawn) {
        this.team2ShopSpawn = team2ShopSpawn;
    }

    public List<Location> getVillagersSpawnPoint() {
        return villagersSpawnPoint;
    }

    public void addVillagerSpawnPoint(Location loc) {
        villagersSpawnPoint.add(loc);
    }

    public boolean isConfigured() {
        if (world == null) return false;
        if (lobbySpawn == null) return false;
        if (spectatorSpawn == null) return false;
        if (team1Spawns.size() < 4 || team2Spawns.size() < 4) return false;
        if (team1Spawns.stream().anyMatch(Objects::isNull)) return false;
        if (team2Spawns.stream().anyMatch(Objects::isNull)) return false;
        if (villagersSpawnPoint.isEmpty()) return false;
        return team1ShopSpawn != null && team2ShopSpawn != null;
    }
}
