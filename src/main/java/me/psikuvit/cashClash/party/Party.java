package me.psikuvit.cashClash.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a party of players.
 */
public class Party {

    private final UUID partyId;
    private final Set<UUID> members;
    private final long createdAt;
    private UUID owner;

    public Party(UUID owner) {
        this.partyId = UUID.randomUUID();
        this.owner = owner;
        this.members = new HashSet<>();
        this.members.add(owner);
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean addMember(UUID playerId) {
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        if (playerId.equals(owner)) {
            return false; // Can't remove owner directly
        }
        return members.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    public int getSize() {
        return members.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Transfer ownership to another member.
     */
    public boolean transferOwnership(UUID newOwner) {
        if (!members.contains(newOwner)) {
            return false;
        }
        this.owner = newOwner;
        return true;
    }

    /**
     * Get all online members.
     */
    public Set<Player> getOnlineMembers() {
        Set<Player> online = new HashSet<>();
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * Get the owner as a Player (if online).
     */
    public Player getOwnerPlayer() {
        return Bukkit.getPlayer(owner);
    }

    /**
     * Disband the party by removing all members.
     */
    public void disband() {
        members.clear();
    }
}

