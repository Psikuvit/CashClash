package me.psikuvit.cashClash.gamemode;

/**
 * Outcome of a single {@link SuddenDeathManager#tickSuddenDeathCycle()} call.
 */
public enum CycleTickResult {
    RUNNING,
    RESTARTED,
    RESOLVED,
    INACTIVE
}
