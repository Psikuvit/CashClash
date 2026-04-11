package me.psikuvit.cashClash.gamemode.impl;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;

import java.util.UUID;

/**
 * Record representing a flag state in Capture the Flag gamemode
 * Consolidates flag-related data: holder, capture time, base location, plate location, and banner
 */
public record FlagState(
        UUID holder,
        long captureTime,
        Location flagBase,
        Location capturePlate,
        BlockDisplay bannerDisplay,
        double bannerAngle
) {
    /**
     * Create a new flag state with default values
     */
    public static FlagState create() {
        return new FlagState(null, 0, null, null, null, 0.0);
    }

    /**
     * Create a flag state with a holder picked up
     */
    public FlagState withHolder(UUID holderUuid, long captureTime) {
        return new FlagState(holderUuid, captureTime, this.flagBase, this.capturePlate, this.bannerDisplay, this.bannerAngle);
    }

    /**
     * Create a flag state with holder removed
     */
    public FlagState withoutHolder() {
        return new FlagState(null, 0, this.flagBase, this.capturePlate, this.bannerDisplay, this.bannerAngle);
    }

    /**
     * Create a flag state with base location set
     */
    public FlagState withBase(Location base) {
        return new FlagState(this.holder, this.captureTime, base, this.capturePlate, this.bannerDisplay, this.bannerAngle);
    }

    /**
     * Create a flag state with capture plate location set
     */
    public FlagState withCapturePlate(Location plate) {
        return new FlagState(this.holder, this.captureTime, this.flagBase, plate, this.bannerDisplay, this.bannerAngle);
    }

    /**
     * Create a flag state with banner display set
     */
    public FlagState withBannerDisplay(BlockDisplay banner) {
        return new FlagState(this.holder, this.captureTime, this.flagBase, this.capturePlate, banner, this.bannerAngle);
    }

    /**
     * Create a flag state with updated banner angle
     */
    public FlagState withBannerAngle(double angle) {
        return new FlagState(this.holder, this.captureTime, this.flagBase, this.capturePlate, this.bannerDisplay, angle);
    }

    /**
     * Check if flag is currently held
     */
    public boolean isHeld() {
        return holder != null;
    }
}

