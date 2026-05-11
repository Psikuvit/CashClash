package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.gamemode.impl.FlagState;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Location;

import java.util.Map;

/**
 * Utility class for managing base flag mechanics
 * Handles flag returns, drops, and state checks
 */
public class FlagBaseMechanicsUtils {

    private FlagBaseMechanicsUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Check if a flag is currently dropped and waiting for return
     * A flag is considered dropped if:
     * - It is not held by a player
     * - It is not at its base location
     * - A return timer is active (expiry is set)
     *
     * @param teamNumber The team number
     * @param flagStates Map of flag states
     * @param flagBaseLocations Map of base locations
     * @param flagReturnExpiry Map of return expiry times
     * @return True if flag is dropped and waiting for return
     */
    public static boolean isDroppedFlagWaitingForReturn(
            int teamNumber,
            Map<Integer, FlagState> flagStates,
            Map<Integer, Location> flagBaseLocations,
            Map<Integer, Long> flagReturnExpiry) {

        FlagState flag = flagStates.get(teamNumber);
        Location base = flagBaseLocations.get(teamNumber);

        if (flag == null || flag.isHeld() || flag.flagLoc() == null || base == null) {
            return false;
        }

        // Flag is dropped if it's not at base and has an active return expiry
        boolean isAwayFromBase = flag.flagLoc().getWorld() == base.getWorld()
                && flag.flagLoc().distanceSquared(base) > 0.25;

        return isAwayFromBase && flagReturnExpiry.containsKey(teamNumber);
    }

    /**
     * Get the time remaining until a dropped flag returns to base
     *
     * @param teamNumber The team number
     * @param flagReturnExpiry Map of expiry times
     * @return Remaining time in seconds, or 0 if no active timer
     */
    public static long getRemainingReturnSeconds(int teamNumber, Map<Integer, Long> flagReturnExpiry) {
        Long expiry = flagReturnExpiry.get(teamNumber);
        if (expiry == null) {
            return 0;
        }
        long remainingMs = Math.max(0L, expiry - System.currentTimeMillis());
        return remainingMs / 1000L + (remainingMs % 1000L > 0 ? 1 : 0);
    }

    /**
     * Reset a flag to its base location
     *
     * @param teamNumber The team number
     * @param flagStates Map of flag states
     * @param flagBaseLocations Map of base locations
     * @return The updated FlagState at base
     */
    public static FlagState returnFlagToBase(
            int teamNumber,
            Map<Integer, FlagState> flagStates,
            Map<Integer, Location> flagBaseLocations) {

        FlagState flag = flagStates.get(teamNumber);
        Location baseLocation = flagBaseLocations.get(teamNumber);

        if (flag == null || baseLocation == null) {
            return flag;
        }

        FlagState returnedFlag = flag.withoutHolder()
                .withFlagLoc(baseLocation.clone())
                .withCarryingTask(null);

        flagStates.put(teamNumber, returnedFlag);
        Messages.debug("[CTF] Returned Team " + teamNumber + " flag to base");

        return returnedFlag;
    }

    /**
     * Drop a flag at a specific location
     *
     * @param teamNumber The team number
     * @param dropLocation The location to drop at
     * @param flagStates Map of flag states
     * @return The updated FlagState (dropped)
     */
    public static FlagState dropFlagAtLocation(
            int teamNumber,
            Location dropLocation,
            Map<Integer, FlagState> flagStates) {

        FlagState flag = flagStates.get(teamNumber);
        if (flag == null || dropLocation == null) {
            return flag;
        }

        Location adjustedLocation = dropLocation.clone();
        if (adjustedLocation.getWorld() == null) {
            // This should not happen, but be defensive
            return flag;
        }

        FlagState droppedFlag = flag.withoutHolder()
                .withFlagLoc(adjustedLocation)
                .withCarryingTask(null);

        flagStates.put(teamNumber, droppedFlag);
        Messages.debug("[CTF] Dropped Team " + teamNumber + " flag at " + adjustedLocation);

        return droppedFlag;
    }

    /**
     * Reset flags after a capture (clear the flag that was captured)
     *
     * @param capturingTeam The team that captured
     * @param flagStates Map of flag states
     * @param flagBaseLocations Map of base locations
     */
    public static void resetFlagsAfterCapture(
            int capturingTeam,
            Map<Integer, FlagState> flagStates,
            Map<Integer, Location> flagBaseLocations) {

        int enemyTeam = capturingTeam == 1 ? 2 : 1;
        returnFlagToBase(enemyTeam, flagStates, flagBaseLocations);
    }
}

