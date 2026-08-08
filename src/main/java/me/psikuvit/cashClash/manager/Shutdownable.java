package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;

/**
 * Implemented by singleton managers that hold state/tasks needing an orderly shutdown on
 * plugin disable. {@link CashClashPlugin#onDisable()} iterates every registered
 * {@code Shutdownable} instead of hand-listing one call per manager.
 */
public interface Shutdownable {

    void shutdown();
}
