package me.psikuvit.cashClash.gamemode.impl;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * State for one Kill Confirm capture zone: the 3x3 area spawned at a death location that the
 * killer's team can confirm (point / money / heart) or the victim's team can deny. Not a
 * record - occupant hold-timers and the spawned display entities are mutated in place while
 * the zone is active.
 */
public class KCZone {

    public enum ZoneKind {
        NAMETAG,
        MONEY,
        HEART
    }

    private final Location center;
    private final int killerTeam;
    private final String victimName;
    private final ZoneKind kind;
    private final long activatesAtMs;
    private final long expiresAtMs;

    // Per-player hold-start timestamp (ms). A player's progress resets whenever they leave and
    // re-enter the zone - removing their entry here is how that reset is implemented.
    private final Map<UUID, Long> occupantEntryTimestamps = new HashMap<>();

    private BlockDisplay platformDisplay;
    private Entity iconDisplay;

    public KCZone(Location center, int killerTeam, String victimName, ZoneKind kind, long activatesAtMs, long expiresAtMs) {
        this.center = center;
        this.killerTeam = killerTeam;
        this.victimName = victimName;
        this.kind = kind;
        this.activatesAtMs = activatesAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    public Location getCenter() {
        return center;
    }

    public int getKillerTeam() {
        return killerTeam;
    }

    public String getVictimName() {
        return victimName;
    }

    public ZoneKind getKind() {
        return kind;
    }

    public long getActivatesAtMs() {
        return activatesAtMs;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public Map<UUID, Long> getOccupantEntryTimestamps() {
        return occupantEntryTimestamps;
    }

    public BlockDisplay getPlatformDisplay() {
        return platformDisplay;
    }

    public void setPlatformDisplay(BlockDisplay platformDisplay) {
        this.platformDisplay = platformDisplay;
    }

    public Entity getIconDisplay() {
        return iconDisplay;
    }

    public void setIconDisplay(Entity iconDisplay) {
        this.iconDisplay = iconDisplay;
    }

    public boolean isPendingActivation(long now) {
        return now < activatesAtMs;
    }

    public boolean isExpired(long now) {
        return now >= expiresAtMs;
    }
}
