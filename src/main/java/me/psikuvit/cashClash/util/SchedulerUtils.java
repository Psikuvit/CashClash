package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.IllegalPluginAccessException;

/**
 * Utility wrapper for scheduling tasks that safely handles plugin-disable races.
 */
public class SchedulerUtils {

    private SchedulerUtils() {
        throw new AssertionError("Nope.");
    }

    public static BukkitTask runTask(Runnable runnable) {
        if (!CashClashPlugin.getInstance().isEnabled()) return null;
        try {
            return Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), runnable);
        } catch (IllegalPluginAccessException ex) {
            CashClashPlugin.getInstance().getLogger().warning("Scheduler prevented runTask: " + ex.getMessage());
            return null;
        }
    }

    public static BukkitTask runTaskLater(Runnable runnable, long ticks) {
        if (!CashClashPlugin.getInstance().isEnabled()) return null;
        try {
            return Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), runnable, ticks);
        } catch (IllegalPluginAccessException ex) {
            CashClashPlugin.getInstance().getLogger().warning("Scheduler prevented runTaskLater: " + ex.getMessage());
            return  null;
        }
    }

    public static BukkitTask runTaskAsync(Runnable runnable) {
        if (!CashClashPlugin.getInstance().isEnabled()) return null;
        try {
            return Bukkit.getScheduler().runTaskAsynchronously(CashClashPlugin.getInstance(), runnable);
        } catch (IllegalPluginAccessException ex) {
            CashClashPlugin.getInstance().getLogger().warning("Scheduler prevented runTaskAsync: " + ex.getMessage());
            return null;
        }
    }

    public static BukkitTask runTaskTimer(Runnable runnable, long delay, long period) {
        if (!CashClashPlugin.getInstance().isEnabled()) return null;
        try {
            return Bukkit.getScheduler().runTaskTimer(CashClashPlugin.getInstance(), runnable, delay, period);
        } catch (IllegalPluginAccessException ex) {
            CashClashPlugin.getInstance().getLogger().warning("Scheduler prevented runTaskTimer: " + ex.getMessage());
            return null;
        }
    }
}
