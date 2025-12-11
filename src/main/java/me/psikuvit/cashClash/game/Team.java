package me.psikuvit.cashClash.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a team in Cash Clash (4 players max)
 */
public class Team {

    private final int teamNumber;
    private final Set<UUID> players;
    private boolean forfeitVoted;
    private final Set<UUID> forfeitVotes;
    private boolean enderPearlsDisabled;
    private long enderPearlReenableTime;
    // Forfeit timing: when the 2nd teammate died (ms), used to enforce delay before allowing forfeit
    private long forfeitStartTime;

    public Team(int teamNumber) {
        this.teamNumber = teamNumber;
        this.players = new HashSet<>();
        this.forfeitVotes = new HashSet<>();
        this.forfeitVoted = false;
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
        forfeitVoted = false;
    }

    public int getAliveCount() {
        // TODO: Implement based on player lives
        return players.size();
    }

    // Getters and setters
    public int getTeamNumber() {
        return teamNumber;
    }

    public Set<UUID> getPlayers() {
        return new HashSet<>(players);
    }

    public int getSize() {
        return players.size();
    }

    public boolean isForfeitVoted() {
        return forfeitVoted;
    }

    public void setForfeitVoted(boolean voted) {
        this.forfeitVoted = voted;
    }

    public Set<UUID> getForfeitVotes() {
        return new HashSet<>(forfeitVotes);
    }

    public boolean isEnderPearlsDisabled() {
        return enderPearlsDisabled;
    }

    public void setEnderPearlsDisabled(boolean disabled) {
        this.enderPearlsDisabled = disabled;
    }

    public long getEnderPearlReenableTime() {
        return enderPearlReenableTime;
    }

    public void setEnderPearlReenableTime(long time) {
        this.enderPearlReenableTime = time;
    }

    public void setForfeitStartTime(long time) {
        this.forfeitStartTime = time;
    }

    public long getForfeitStartTime() {
        return this.forfeitStartTime;
    }

    public void resetForfeitStartTime() {
        this.forfeitStartTime = 0L;
    }
}
