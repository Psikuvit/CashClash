package me.psikuvit.cashClash.gamemode.impl;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Record representing a flag state in Capture the Flag gamemode
 * Consolidates flag-related data: holder, capture time, capture plate (banner location), and banner
 * Note: Flag base is always capture plate + 2 blocks up, so it's calculated on-demand
 */
public record FlagState(
        UUID holder,
        long captureTime,
        Location flagLoc,
        BlockDisplay bannerDisplay,
        double bannerAngle,
        BukkitTask carryingTask,
        double carryingAngle
) {
    /**
     * Create a new flag state with default values
     */
    public static FlagState create() {
        return new FlagState(null, 0, null, null, 0.0, null, 0.0);
    }

    /**
     * Create a flag state with a holder picked up
     */
    public FlagState withHolder(UUID holderUuid, long captureTime) {
        return new FlagState(holderUuid, captureTime, this.flagLoc, this.bannerDisplay, this.bannerAngle, this.carryingTask, this.carryingAngle);
    }

    /**
     * Create a flag state with holder removed
     */
    public FlagState withoutHolder() {
        return new FlagState(null, 0, this.flagLoc, this.bannerDisplay, this.bannerAngle, this.carryingTask, this.carryingAngle);
    }

    /**
     * Create a flag state with an updated current pickup location.
     */
    public FlagState withFlagLoc(Location location) {
        return new FlagState(this.holder, this.captureTime, location, this.bannerDisplay, this.bannerAngle, this.carryingTask, this.carryingAngle);
    }

    /**
     * Create a flag state with banner display set
     */
    public FlagState withBannerDisplay(BlockDisplay banner) {
        return new FlagState(this.holder, this.captureTime, this.flagLoc, banner, this.bannerAngle, this.carryingTask, this.carryingAngle);
    }

    /**
     * Create a flag state with updated banner angle
     */
    public FlagState withBannerAngle(double angle) {
        return new FlagState(this.holder, this.captureTime, this.flagLoc, this.bannerDisplay, angle, this.carryingTask, this.carryingAngle);
    }

    /**
     * Create a flag state with carrying task
     */
    public FlagState withCarryingTask(BukkitTask task) {
        return new FlagState(this.holder, this.captureTime, this.flagLoc, this.bannerDisplay, this.bannerAngle, task, this.carryingAngle);
    }

    /**
     * Create a flag state with updated carrying angle
     */
    public FlagState withCarryingAngle(double angle) {
        return new FlagState(this.holder, this.captureTime, this.flagLoc, this.bannerDisplay, this.bannerAngle, this.carryingTask, angle);
    }

    /**
     * Get flag base location (2 blocks above capture plate)
     */
    public Location getFlagLoc() {
        return flagLoc != null ? flagLoc.clone().add(0, 2, 0) : null;
    }

    /**
     * Check if flag is currently held
     */
    public boolean isHeld() {
        return holder != null;
    }
}

