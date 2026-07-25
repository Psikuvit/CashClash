package me.psikuvit.cashClash.gamemode.impl;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

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
    private TextDisplay timerDisplay;

    // Whether the 0.5s grey "pending activation" visuals have already been swapped to the
    // lit, kind-colored state. Guards KCZoneUtils#activateZoneEntities from re-running per tick.
    private boolean activated;

    // Last whole-second value written to timerDisplay, so KCZoneUtils#updateTimerDisplay only
    // touches the entity when the displayed number actually needs to change.
    private int lastDisplayedTimerSeconds = -1;

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

    public TextDisplay getTimerDisplay() {
        return timerDisplay;
    }

    public void setTimerDisplay(TextDisplay timerDisplay) {
        this.timerDisplay = timerDisplay;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public int getLastDisplayedTimerSeconds() {
        return lastDisplayedTimerSeconds;
    }

    public void setLastDisplayedTimerSeconds(int lastDisplayedTimerSeconds) {
        this.lastDisplayedTimerSeconds = lastDisplayedTimerSeconds;
    }

    public boolean isPendingActivation(long now) {
        return now < activatesAtMs;
    }

    public boolean isExpired(long now) {
        return now >= expiresAtMs;
    }
}
