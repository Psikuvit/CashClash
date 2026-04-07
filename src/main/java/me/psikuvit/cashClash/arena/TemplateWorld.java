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

    private final List<Location> teamRedSpawns;
    private final List<Location> teamBlueSpawns;

    // Shop spawn points for each team (template-space)
    private Location teamRedShopSpawn;
    private Location teamBlueShopSpawn;
    private final List<Location> villagersSpawnPoint;

    // CTF Capture plate locations
    private Location ctfCaptureTeamRedPlate;
    private Location ctfCaptureTeamBluePlate;

    public TemplateWorld(String id, World world) {
        this.id = id;
        this.world = world;
        this.teamRedSpawns = new ArrayList<>();
        this.teamBlueSpawns = new ArrayList<>();
        this.villagersSpawnPoint = new ArrayList<>();
        this.ctfCaptureTeamRedPlate = null;
        this.ctfCaptureTeamBluePlate = null;
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

    public void setTeamRedSpawn(int idx, Location loc) {
        ensureListSize(teamRedSpawns, idx + 1);
        teamRedSpawns.set(idx, loc);
    }

    public void setTeamBlueSpawn(int idx, Location loc) {
        ensureListSize(teamBlueSpawns, idx + 1);
        teamBlueSpawns.set(idx, loc);
    }

    private void ensureListSize(List<Location> list, int size) {
        while (list.size() < size) list.add(null);
    }

    public Location getTeamRedSpawn(int idx) {
        if (idx < 0 || idx >= teamRedSpawns.size()) return null;
        return teamRedSpawns.get(idx);
    }

    public Location getTeamBlueSpawn(int idx) {
        if (idx < 0 || idx >= teamBlueSpawns.size()) return null;
        return teamBlueSpawns.get(idx);
    }

    public Location getTeamRedShopSpawn() {
        return teamRedShopSpawn;
    }

    public void setTeamRedShopSpawn(Location teamRedShopSpawn) {
        this.teamRedShopSpawn = teamRedShopSpawn;
    }

    public Location getTeamBlueShopSpawn() {
        return teamBlueShopSpawn;
    }
    public void setTeamBlueShopSpawn(Location teamBlueShopSpawn) {
        this.teamBlueShopSpawn = teamBlueShopSpawn;
    }

    public List<Location> getVillagersSpawnPoint() {
        return villagersSpawnPoint;
    }

    public void addVillagerSpawnPoint(Location loc) {
        villagersSpawnPoint.add(loc);
    }

    // CTF Capture plate getters/setters
    public Location getCTFCaptureTeamRedPlate() {
        return ctfCaptureTeamRedPlate;
    }

    public void setCTFCaptureTeamRedPlate(Location loc) {
        this.ctfCaptureTeamBluePlate = loc;
    }

    public Location getCTFCaptureTeamBluePlate() {
        return ctfCaptureTeamBluePlate;
    }

    public void setCTFCaptureTeamBluePlate(Location loc) {
        this.ctfCaptureTeamBluePlate = loc;
    }

    public boolean isConfigured() {
        if (world == null) return false;
        if (lobbySpawn == null) return false;
        if (spectatorSpawn == null) return false;
        if (teamRedSpawns.size() < 4 || teamBlueSpawns.size() < 4) return false;
        if (teamRedSpawns.stream().anyMatch(Objects::isNull)) return false;
        if (teamBlueSpawns.stream().anyMatch(Objects::isNull)) return false;
        if (villagersSpawnPoint.isEmpty()) return false;
        return teamRedShopSpawn != null && teamBlueShopSpawn != null;
    }
}
