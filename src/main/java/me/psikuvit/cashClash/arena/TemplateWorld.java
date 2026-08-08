package me.psikuvit.cashClash.arena;

import me.psikuvit.cashClash.util.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Holds information about a template world used by arenas (template id -> world name). Resolves
 * the World live by name on every access instead of caching a reference, so a reload/unload
 * elsewhere can never leave this holding a stale World.
 */
public class TemplateWorld {

    private final String id;
    private String worldName;

    private Location lobbySpawn;
    private Location spectatorSpawn;

    private final List<Location> teamRedSpawns;
    private final List<Location> teamBlueSpawns;

    // Shop spawn points for each team (template-space)
    private Location teamRedShopSpawn;
    private Location teamBlueShopSpawn;
    private final List<Location> villagersSpawnPoint;

    // CTF Flag locations
    private Location redFlagLoc;
    private Location blueFlagLoc;

    public TemplateWorld(String id, World world) {
        this.id = id;
        this.worldName = world != null ? world.getName() : null;
        this.teamRedSpawns = new ArrayList<>();
        this.teamBlueSpawns = new ArrayList<>();
        this.villagersSpawnPoint = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    /**
     * @return the live World, resolved by name - null if unset or not currently loaded.
     */
    public World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }
    public void setWorld(World world) {
        this.worldName = world != null ? world.getName() : null;
    }
    public String getWorldName() {
        return worldName;
    }
    /**
     * @return a clone of the lobby spawn - callers can freely mutate the result without
     *         corrupting this template's stored location.
     */
    public Location getLobbySpawn() {
        return LocationUtils.clone(lobbySpawn);
    }
    public void setSpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }
    public Location getSpectatorSpawn() {
        return LocationUtils.clone(spectatorSpawn);
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
        return LocationUtils.clone(teamRedSpawns.get(idx));
    }

    public Location getTeamBlueSpawn(int idx) {
        if (idx < 0 || idx >= teamBlueSpawns.size()) return null;
        return LocationUtils.clone(teamBlueSpawns.get(idx));
    }

    public Location getTeamRedShopSpawn() {
        return LocationUtils.clone(teamRedShopSpawn);
    }
    public void setTeamRedShopSpawn(Location teamRedShopSpawn) {
        this.teamRedShopSpawn = teamRedShopSpawn;
    }
    public Location getTeamBlueShopSpawn() {
        return LocationUtils.clone(teamBlueShopSpawn);
    }
    public void setTeamBlueShopSpawn(Location teamBlueShopSpawn) {
        this.teamBlueShopSpawn = teamBlueShopSpawn;
    }

    /**
     * @return a defensive copy - both the list and each Location in it are safe for the caller
     *         to mutate without affecting this template.
     */
    public List<Location> getVillagersSpawnPoint() {
        return villagersSpawnPoint.stream().map(LocationUtils::clone).collect(Collectors.toList());
    }
    public void addVillagerSpawnPoint(Location loc) {
        villagersSpawnPoint.add(loc);
    }

    /**
     * Get Red flag location (CTF mode)
     */
    public Location getRedFlagLoc() {
        return LocationUtils.clone(redFlagLoc);
    }

    /**
     * Set Red flag location (CTF mode)
     */
    public void setRedFlagLoc(Location loc) {
        this.redFlagLoc = loc;
    }

    /**
     * Get Blue flag location (CTF mode)
     */
    public Location getBlueFlagLoc() {
        return LocationUtils.clone(blueFlagLoc);
    }

    /**
     * Set Blue flag location (CTF mode)
     */
    public void setBlueFlagLoc(Location loc) {
        this.blueFlagLoc = loc;
    }

    public boolean isConfigured() {
        if (worldName == null) return false;
        if (lobbySpawn == null) return false;
        if (spectatorSpawn == null) return false;
        if (teamRedSpawns.size() < 4 || teamBlueSpawns.size() < 4) return false;
        if (teamRedSpawns.stream().anyMatch(Objects::isNull)) return false;
        if (teamBlueSpawns.stream().anyMatch(Objects::isNull)) return false;
        if (villagersSpawnPoint.isEmpty()) return false;
        if (redFlagLoc == null || blueFlagLoc == null) return false;
        return teamRedShopSpawn != null && teamBlueShopSpawn != null;
    }
}
