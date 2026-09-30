package me.psikuvit.cashClash.game;

import me.psikuvit.cashClash.util.enums.TeamColor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a team in Cash Clash (4 players max)
 */
public class Team {

    private final int teamNumber;
    private final TeamColor color;
    private final Set<UUID> players;
    private final Map<UUID, Boolean> readyStatus;
    private final boolean enderPearlsDisabled;

    public Team(int teamNumber) {
        this.teamNumber = teamNumber;
        this.color = teamNumber == 1 ? TeamColor.RED : TeamColor.BLUE;
        this.players = new HashSet<>();
        this.readyStatus = new ConcurrentHashMap<>();
        this.enderPearlsDisabled = false;
    }

    public void addPlayer(UUID uuid) {
        if (players.size() < 4) {
            players.add(uuid);
            readyStatus.put(uuid, false);
        }
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        readyStatus.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public void toggleReadyStatus(UUID uuid) {
        if (readyStatus.containsKey(uuid)) {
            readyStatus.put(uuid, !readyStatus.get(uuid));
        }
    }

    public void resetReadyStatus() {
        readyStatus.replaceAll((u, v) -> false);
    }

    // Getters and setters
    public int getTeamNumber() {
        return teamNumber;
    }

    public TeamColor getColor() {
        return color;
    }

    /**
     * Get the display name with color (e.g., "<red>Red</red>")
     */
    public String getColoredName() {
        return color.getColoredName();
    }

    /**
     * Get just the team name (e.g., "Red" or "Blue")
     */
    public String getName() {
        return color.getDisplayName();
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public int getSize() {
        return players.size();
    }

    public boolean isEnderPearlsDisabled() {
        return enderPearlsDisabled;
    }

    public boolean isTeamReady() {
        for (boolean status : readyStatus.values()) {
            if (!status) {
                return false;
            }
        }
        return true;
    }

    public boolean isPlayerReady(UUID uuid) {
        return readyStatus.getOrDefault(uuid, false);
    }

}
