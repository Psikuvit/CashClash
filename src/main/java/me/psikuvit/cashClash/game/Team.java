package me.psikuvit.cashClash.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a team in Cash Clash (4 players max)
 */
public class Team {

    /**
     * Team color identifiers
     */
    public enum TeamColor {
        RED("Red", "<red>"),
        BLUE("Blue", "<blue>");

        private final String displayName;
        private final String miniMessageColor;

        TeamColor(String displayName, String miniMessageColor) {
            this.displayName = displayName;
            this.miniMessageColor = miniMessageColor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getMiniMessageColor() {
            return miniMessageColor;
        }

        public String getColoredName() {
            return miniMessageColor + displayName + "</" + displayName.toLowerCase() + ">";
        }
    }

    private final int teamNumber;
    private final TeamColor color;
    private final Set<UUID> players;
    private final Set<UUID> forfeitVotes;
    private final boolean enderPearlsDisabled;
    // Forfeit timing: when the 2nd teammate died (ms), used to enforce delay before allowing forfeit
    private long forfeitStartTime;

    public Team(int teamNumber) {
        this.teamNumber = teamNumber;
        this.color = teamNumber == 1 ? TeamColor.RED : TeamColor.BLUE;
        this.players = new HashSet<>();
        this.forfeitVotes = new HashSet<>();
        this.enderPearlsDisabled = false;
        this.forfeitStartTime = 0L;
    }

    public void addPlayer(UUID uuid) {
        if (players.size() < 4) {
            players.add(uuid);
        }
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        forfeitVotes.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public void addForfeitVote(UUID uuid) {
        forfeitVotes.add(uuid);
    }

    public boolean hasAllForfeitVotes() {
        return forfeitVotes.size() == players.size() && !players.isEmpty();
    }

    public void resetForfeitVotes() {
        forfeitVotes.clear();
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

    public Set<UUID> getForfeitVotes() {
        return forfeitVotes;
    }

    public boolean isEnderPearlsDisabled() {
        return enderPearlsDisabled;
    }

    public void setForfeitStartTime(long time) {
        this.forfeitStartTime = time;
    }

    public long getForfeitStartTime() {
        return this.forfeitStartTime;
    }

}
