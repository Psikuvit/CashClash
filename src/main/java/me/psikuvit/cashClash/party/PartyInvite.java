package me.psikuvit.cashClash.party;

import java.util.UUID;

/**
 * Represents a party invitation request.
 */
public class PartyInvite {

    public static final long INVITE_TIMEOUT_MS = 60_000; // 60 seconds
    private final UUID inviter;
    private final UUID invitee;
    private final UUID partyId;
    private final long timestamp;
    private final long expiresAt;

    public PartyInvite(UUID inviter, UUID invitee, UUID partyId) {
        this.inviter = inviter;
        this.invitee = invitee;
        this.partyId = partyId;
        this.timestamp = System.currentTimeMillis();
        this.expiresAt = timestamp + INVITE_TIMEOUT_MS;
    }

    public UUID getInviter() {
        return inviter;
    }

    public UUID getInvitee() {
        return invitee;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public long getRemainingTime() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}

